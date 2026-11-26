package hag1987haaa.pebble.iron.util

import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.model.WorkoutTypeRegistry
import hag1987haaa.pebble.iron.domain.model.hasValidGpsCoordinate
import hag1987haaa.pebble.iron.domain.model.hasValidGpsRoute
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure-Kotlin Garmin FIT 2.0 Activity binary encoder (Beta).
 *
 * Encodes:
 * - 14-byte FIT header with header CRC-16
 * - file_id (mesg 0)
 * - event (mesg 21): timer start / stop_all
 * - record (mesg 20): timestamp, optional position_lat/position_long (omitted for indoor),
 *   distance, altitude, speed, heart_rate, cadence, power
 * - lap (mesg 19)
 * - session (mesg 18) with exact FIT sport & sub_sport for all 18 workout types
 * - activity (mesg 34)
 * - 2-byte trailing FIT CRC-16
 */
object FitExporter {
    private const val FIT_EPOCH_OFFSET_SECONDS = 631_065_600L // 1989-12-31T00:00:00Z
    private const val PROTOCOL_VERSION: Int = 0x20 // FIT 2.0
    private const val PROFILE_VERSION: Int = 2184 // 21.84

    // Base types (little-endian)
    private const val TYPE_ENUM = 0x00
    private const val TYPE_UINT8 = 0x02
    private const val TYPE_UINT16 = 0x84
    private const val TYPE_SINT32 = 0x85
    private const val TYPE_UINT32 = 0x86

    // Invalid sentinels
    private const val INVALID_UINT8 = 0xFF
    private const val INVALID_UINT16 = 0xFFFF
    private const val INVALID_SINT32 = 0x7FFFFFFF
    private const val INVALID_UINT32 = 0xFFFFFFFFL

    // Global message numbers
    private const val MESG_FILE_ID = 0
    private const val MESG_SESSION = 18
    private const val MESG_LAP = 19
    private const val MESG_RECORD = 20
    private const val MESG_EVENT = 21
    private const val MESG_ACTIVITY = 34

    // Local message numbers
    private const val LOCAL_FILE_ID = 0
    private const val LOCAL_EVENT = 1
    private const val LOCAL_RECORD = 2
    private const val LOCAL_LAP = 3
    private const val LOCAL_SESSION = 4
    private const val LOCAL_ACTIVITY = 5

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB400, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    private data class FieldDef(
        val fieldNum: Int,
        val size: Int,
        val baseType: Int,
    )

    private class ByteBuffer {
        private var buf = ByteArray(4096)
        var size: Int = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra <= buf.size) return
            var next = buf.size * 2
            while (next < size + extra) next *= 2
            buf = buf.copyOf(next)
        }

        fun writeByte(v: Int) {
            ensure(1)
            buf[size++] = (v and 0xFF).toByte()
        }

        fun writeUInt16LE(v: Int) {
            ensure(2)
            buf[size++] = (v and 0xFF).toByte()
            buf[size++] = ((v ushr 8) and 0xFF).toByte()
        }

        fun writeSInt32LE(v: Int) {
            ensure(4)
            buf[size++] = (v and 0xFF).toByte()
            buf[size++] = ((v ushr 8) and 0xFF).toByte()
            buf[size++] = ((v ushr 16) and 0xFF).toByte()
            buf[size++] = ((v ushr 24) and 0xFF).toByte()
        }

        fun writeUInt32LE(v: Long) {
            ensure(4)
            buf[size++] = (v and 0xFFL).toByte()
            buf[size++] = ((v ushr 8) and 0xFFL).toByte()
            buf[size++] = ((v ushr 16) and 0xFFL).toByte()
            buf[size++] = ((v ushr 24) and 0xFFL).toByte()
        }

        fun writeBytes(bytes: ByteArray) {
            ensure(bytes.size)
            bytes.copyInto(buf, destinationOffset = size)
            size += bytes.size
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    fun computeCrc16(bytes: ByteArray, start: Int = 0, endExclusive: Int = bytes.size): Int {
        var crc = 0
        for (i in start until endExclusive) {
            val byteVal = bytes[i].toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0x0F]
            crc = (crc ushr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[byteVal and 0x0F]

            tmp = CRC_TABLE[crc and 0x0F]
            crc = (crc ushr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(byteVal ushr 4) and 0x0F]
        }
        return crc and 0xFFFF
    }

    fun toFitTimestamp(instant: Instant): Long =
        (instant.epochSeconds - FIT_EPOCH_OFFSET_SECONDS).coerceAtLeast(0L)

    fun degreesToSemicircles(degrees: Double): Int =
        (degrees * (2147483648.0 / 180.0)).roundToInt()

    private fun ByteBuffer.writeDefinition(localNum: Int, globalNum: Int, fields: List<FieldDef>) {
        writeByte(0x40 or (localNum and 0x0F)) // Definition header
        writeByte(0) // Reserved
        writeByte(0) // Architecture: 0 = little-endian
        writeUInt16LE(globalNum)
        writeByte(fields.size)
        for (f in fields) {
            writeByte(f.fieldNum)
            writeByte(f.size)
            writeByte(f.baseType)
        }
    }

    private fun ByteBuffer.writeDataHeader(localNum: Int) {
        writeByte(localNum and 0x0F)
    }

    fun export(rawRun: RunActivity): ByteArray {
        val run = rawRun.withSanitizedTimestamps()
        val def = WorkoutTypeRegistry.getDefinition(run.type)
        val hasGps = run.route.hasValidGpsRoute()

        val startTs = toFitTimestamp(run.startTime)
        val endInstant = run.endTime ?: Instant.fromEpochSeconds(run.startTime.epochSeconds + run.durationSeconds.coerceAtLeast(1L))
        val endTs = toFitTimestamp(endInstant).coerceAtLeast(startTs + 1L)
        val elapsedMs = ((endInstant.toEpochMilliseconds() - run.startTime.toEpochMilliseconds()).coerceAtLeast(1000L))
        val timerMs = (run.durationSeconds.coerceAtLeast(1L) * 1000L)
        val totalDistanceCm = (run.distanceMeters.coerceAtLeast(0.0) * 100.0).roundToLong()
        val totalCalories = run.calories?.roundToInt()?.coerceIn(0, 65534) ?: INVALID_UINT16
        val totalAscent = run.elevationGain?.roundToInt()?.coerceIn(0, 65534) ?: INVALID_UINT16
        val avgHr = run.avgHeartRate?.roundToInt()?.coerceIn(1, 254) ?: INVALID_UINT8
        val maxHr = run.maxHeartRate?.roundToInt()?.coerceIn(1, 254) ?: INVALID_UINT8
        val avgCad = run.avgCadence?.roundToInt()?.coerceIn(0, 254) ?: INVALID_UINT8
        val maxCad = run.maxCadence?.roundToInt()?.coerceIn(0, 254) ?: INVALID_UINT8
        val avgPow = run.avgPower?.roundToInt()?.coerceIn(0, 65534) ?: INVALID_UINT16
        val maxPow = run.maxPower?.roundToInt()?.coerceIn(0, 65534) ?: INVALID_UINT16

        val data = ByteBuffer()

        // 1. file_id (global 0)
        val fileIdFields = listOf(
            FieldDef(0, 1, TYPE_ENUM),   // type = 4 (activity)
            FieldDef(1, 2, TYPE_UINT16), // manufacturer = 255 (development)
            FieldDef(2, 2, TYPE_UINT16), // product = 1
            FieldDef(3, 4, TYPE_UINT32), // serial_number
            FieldDef(4, 4, TYPE_UINT32), // time_created
        )
        data.writeDefinition(LOCAL_FILE_ID, MESG_FILE_ID, fileIdFields)
        data.writeDataHeader(LOCAL_FILE_ID)
        data.writeByte(4)
        data.writeUInt16LE(255)
        data.writeUInt16LE(1)
        data.writeUInt32LE(1987L)
        data.writeUInt32LE(startTs)

        // 2. event (global 21) — timer start
        val eventFields = listOf(
            FieldDef(253, 4, TYPE_UINT32), // timestamp
            FieldDef(0, 1, TYPE_ENUM),     // event (0 = timer)
            FieldDef(1, 1, TYPE_ENUM),     // event_type (0 = start, 4 = stop_all)
            FieldDef(4, 1, TYPE_UINT8),    // event_group
        )
        data.writeDefinition(LOCAL_EVENT, MESG_EVENT, eventFields)
        data.writeDataHeader(LOCAL_EVENT)
        data.writeUInt32LE(startTs)
        data.writeByte(0)
        data.writeByte(0)
        data.writeByte(0)

        // 3. record (global 20)
        val recordFields = buildList {
            add(FieldDef(253, 4, TYPE_UINT32)) // timestamp
            if (hasGps) {
                add(FieldDef(0, 4, TYPE_SINT32)) // position_lat
                add(FieldDef(1, 4, TYPE_SINT32)) // position_long
            }
            add(FieldDef(5, 4, TYPE_UINT32)) // distance (cm)
            add(FieldDef(2, 2, TYPE_UINT16)) // altitude ((m + 500) * 5)
            add(FieldDef(6, 2, TYPE_UINT16)) // speed (m/s * 1000)
            add(FieldDef(3, 1, TYPE_UINT8))  // heart_rate (bpm)
            add(FieldDef(4, 1, TYPE_UINT8))  // cadence (rpm/spm)
            add(FieldDef(7, 2, TYPE_UINT16)) // power (watts)
        }
        data.writeDefinition(LOCAL_RECORD, MESG_RECORD, recordFields)

        var accumulatedMeters = 0.0
        var prevLat: Double? = null
        var prevLon: Double? = null
        var prevTimeMs: Long? = null

        for (pt in run.route) {
            val ptTimeMs = pt.timestamp.toEpochMilliseconds()
            if (hasGps && pt.hasValidGpsCoordinate()) {
                if (!pt.isSegmentStart && prevLat != null && prevLon != null) {
                    accumulatedMeters += LocationUtils.calculateDistance(prevLat, prevLon, pt.latitude, pt.longitude)
                }
                prevLat = pt.latitude
                prevLon = pt.longitude
            } else if (!hasGps) {
                val spd = pt.sensorSpeed ?: pt.speed
                if (spd != null && spd > 0.0 && prevTimeMs != null && !pt.isSegmentStart) {
                    val dtSec = ((ptTimeMs - prevTimeMs).coerceIn(0L, 10_000L)) / 1000.0
                    accumulatedMeters += spd * dtSec
                }
            }
            prevTimeMs = ptTimeMs

            data.writeDataHeader(LOCAL_RECORD)
            data.writeUInt32LE(toFitTimestamp(pt.timestamp))
            if (hasGps) {
                if (pt.hasValidGpsCoordinate()) {
                    data.writeSInt32LE(degreesToSemicircles(pt.latitude))
                    data.writeSInt32LE(degreesToSemicircles(pt.longitude))
                } else {
                    data.writeSInt32LE(INVALID_SINT32)
                    data.writeSInt32LE(INVALID_SINT32)
                }
            }
            val distCm = (accumulatedMeters.coerceAtLeast(0.0) * 100.0).roundToLong()
            data.writeUInt32LE(if (hasGps || accumulatedMeters > 0.0) distCm else INVALID_UINT32)

            val altEncoded = pt.altitude
                ?.takeIf { it.isFinite() && it in -500.0..12600.0 }
                ?.let { ((it + 500.0) * 5.0).roundToInt().coerceIn(0, 65534) }
                ?: INVALID_UINT16
            data.writeUInt16LE(altEncoded)

            val effSpeed = pt.sensorSpeed ?: pt.speed
            val speedEncoded = effSpeed
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { (it * 1000.0).roundToInt().coerceIn(0, 65534) }
                ?: INVALID_UINT16
            data.writeUInt16LE(speedEncoded)

            val hrEncoded = pt.heartRate?.takeIf { it in 1..254 } ?: INVALID_UINT8
            data.writeByte(hrEncoded)

            val cadEncoded = pt.cadence?.takeIf { it in 0..254 } ?: INVALID_UINT8
            data.writeByte(cadEncoded)

            val powEncoded = pt.power?.takeIf { it in 0..65534 } ?: INVALID_UINT16
            data.writeUInt16LE(powEncoded)
        }

        // 4. event — timer stop_all
        data.writeDataHeader(LOCAL_EVENT)
        data.writeUInt32LE(endTs)
        data.writeByte(0)
        data.writeByte(4)
        data.writeByte(0)

        // 5. lap (global 19)
        val lapFields = listOf(
            FieldDef(253, 4, TYPE_UINT32), // timestamp
            FieldDef(2, 4, TYPE_UINT32),   // start_time
            FieldDef(7, 4, TYPE_UINT32),   // total_elapsed_time (ms)
            FieldDef(8, 4, TYPE_UINT32),   // total_timer_time (ms)
            FieldDef(9, 4, TYPE_UINT32),   // total_distance (cm)
            FieldDef(11, 2, TYPE_UINT16),  // total_calories
            FieldDef(15, 1, TYPE_UINT8),   // avg_heart_rate
            FieldDef(16, 1, TYPE_UINT8),   // max_heart_rate
            FieldDef(17, 1, TYPE_UINT8),   // avg_cadence
            FieldDef(18, 1, TYPE_UINT8),   // max_cadence
            FieldDef(19, 2, TYPE_UINT16),  // avg_power
            FieldDef(20, 2, TYPE_UINT16),  // max_power
            FieldDef(21, 2, TYPE_UINT16),  // total_ascent
            FieldDef(25, 1, TYPE_ENUM),    // sport
            FieldDef(39, 1, TYPE_ENUM),    // sub_sport
            FieldDef(0, 1, TYPE_ENUM),     // event = 9 (lap)
            FieldDef(1, 1, TYPE_ENUM),     // event_type = 1 (stop)
            FieldDef(254, 2, TYPE_UINT16), // message_index = 0
        )
        data.writeDefinition(LOCAL_LAP, MESG_LAP, lapFields)
        data.writeDataHeader(LOCAL_LAP)
        data.writeUInt32LE(endTs)
        data.writeUInt32LE(startTs)
        data.writeUInt32LE(elapsedMs)
        data.writeUInt32LE(timerMs)
        data.writeUInt32LE(totalDistanceCm)
        data.writeUInt16LE(totalCalories)
        data.writeByte(avgHr)
        data.writeByte(maxHr)
        data.writeByte(avgCad)
        data.writeByte(maxCad)
        data.writeUInt16LE(avgPow)
        data.writeUInt16LE(maxPow)
        data.writeUInt16LE(totalAscent)
        data.writeByte(def.fitSport)
        data.writeByte(def.fitSubSport)
        data.writeByte(9)
        data.writeByte(1)
        data.writeUInt16LE(0)

        // 6. session (global 18)
        val sessionFields = listOf(
            FieldDef(253, 4, TYPE_UINT32), // timestamp
            FieldDef(2, 4, TYPE_UINT32),   // start_time
            FieldDef(7, 4, TYPE_UINT32),   // total_elapsed_time (ms)
            FieldDef(8, 4, TYPE_UINT32),   // total_timer_time (ms)
            FieldDef(9, 4, TYPE_UINT32),   // total_distance (cm)
            FieldDef(11, 2, TYPE_UINT16),  // total_calories
            FieldDef(16, 1, TYPE_UINT8),   // avg_heart_rate
            FieldDef(17, 1, TYPE_UINT8),   // max_heart_rate
            FieldDef(18, 1, TYPE_UINT8),   // avg_cadence
            FieldDef(19, 1, TYPE_UINT8),   // max_cadence
            FieldDef(20, 2, TYPE_UINT16),  // avg_power
            FieldDef(21, 2, TYPE_UINT16),  // max_power
            FieldDef(22, 2, TYPE_UINT16),  // total_ascent
            FieldDef(5, 1, TYPE_ENUM),     // sport
            FieldDef(6, 1, TYPE_ENUM),     // sub_sport
            FieldDef(25, 2, TYPE_UINT16),  // first_lap_index = 0
            FieldDef(26, 2, TYPE_UINT16),  // num_laps = 1
            FieldDef(0, 1, TYPE_ENUM),     // event = 8 (session)
            FieldDef(1, 1, TYPE_ENUM),     // event_type = 1 (stop)
            FieldDef(28, 1, TYPE_ENUM),    // trigger = 0 (activity_end)
            FieldDef(254, 2, TYPE_UINT16), // message_index = 0
        )
        data.writeDefinition(LOCAL_SESSION, MESG_SESSION, sessionFields)
        data.writeDataHeader(LOCAL_SESSION)
        data.writeUInt32LE(endTs)
        data.writeUInt32LE(startTs)
        data.writeUInt32LE(elapsedMs)
        data.writeUInt32LE(timerMs)
        data.writeUInt32LE(totalDistanceCm)
        data.writeUInt16LE(totalCalories)
        data.writeByte(avgHr)
        data.writeByte(maxHr)
        data.writeByte(avgCad)
        data.writeByte(maxCad)
        data.writeUInt16LE(avgPow)
        data.writeUInt16LE(maxPow)
        data.writeUInt16LE(totalAscent)
        data.writeByte(def.fitSport)
        data.writeByte(def.fitSubSport)
        data.writeUInt16LE(0)
        data.writeUInt16LE(1)
        data.writeByte(8)
        data.writeByte(1)
        data.writeByte(0)
        data.writeUInt16LE(0)

        // 7. activity (global 34)
        val activityFields = listOf(
            FieldDef(253, 4, TYPE_UINT32), // timestamp
            FieldDef(0, 4, TYPE_UINT32),   // total_timer_time (ms)
            FieldDef(1, 2, TYPE_UINT16),   // num_sessions = 1
            FieldDef(2, 1, TYPE_ENUM),     // type = 0 (manual)
            FieldDef(3, 1, TYPE_ENUM),     // event = 26 (activity)
            FieldDef(4, 1, TYPE_ENUM),     // event_type = 1 (stop)
        )
        data.writeDefinition(LOCAL_ACTIVITY, MESG_ACTIVITY, activityFields)
        data.writeDataHeader(LOCAL_ACTIVITY)
        data.writeUInt32LE(endTs)
        data.writeUInt32LE(timerMs)
        data.writeUInt16LE(1)
        data.writeByte(0)
        data.writeByte(26)
        data.writeByte(1)

        val payload = data.toByteArray()

        // Assemble 14-byte header + payload + 2-byte trailing CRC
        val out = ByteBuffer()
        out.writeByte(14) // header size
        out.writeByte(PROTOCOL_VERSION)
        out.writeUInt16LE(PROFILE_VERSION)
        out.writeUInt32LE(payload.size.toLong())
        out.writeByte('.'.code)
        out.writeByte('F'.code)
        out.writeByte('I'.code)
        out.writeByte('T'.code)
        val headerWithoutCrc = out.toByteArray()
        val headerCrc = computeCrc16(headerWithoutCrc, 0, 12)
        out.writeUInt16LE(headerCrc)

        out.writeBytes(payload)
        val fileCrc = computeCrc16(payload, 0, payload.size)
        out.writeUInt16LE(fileCrc)

        return out.toByteArray()
    }
}
