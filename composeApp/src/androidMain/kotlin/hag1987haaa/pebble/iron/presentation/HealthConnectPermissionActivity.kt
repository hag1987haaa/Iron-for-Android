package hag1987haaa.pebble.iron.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Health Connect の権限説明を表示するActivity。
 * Android 14+ ではこのインテントフィルタを持つActivityの定義が必須です。
 */
class HealthConnectPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isPrivacyPolicy = intent.action == Intent.ACTION_VIEW
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (isPrivacyPolicy) "プライバシーポリシー" else "Health Connect 連携について",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isPrivacyPolicy) {
                            Text(
                                "Iron は、Health Connect とのデータ同期に際して、あなたの健康データを収集・処理します。このデータは、あなたの許可を得た範囲内（エクササイズ記録、心拍数、位置情報等）でのみ使用され、外部の第三者へ共有されることはありません。データはすべてデバイス内および Health Connect の保護されたストレージに保存されます。"
                            )
                        } else {
                            Text(
                                "Iron はワークアウト記録（心拍数、距離、ルート等）を Health Connect と同期するために権限が必要です。このデータはあなたのデバイス内と Health Connect エコシステム内でのみ管理されます。"
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { finish() }) {
                            Text("閉じる")
                        }
                    }
                }
            }
        }
    }
}
