package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource

data class LibraryLicense(
    val name: String,
    val copyright: String,
    val licenseName: String,
    val licenseText: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val libraries = listOf(
        LibraryLicense(
            "Kotlin & Kotlinx Libraries",
            "Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "Jetpack Compose Multiplatform",
            "Copyright 2020-2024 JetBrains s.r.o. and Google Inc.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "osmdroid",
            "Copyright 2008-2024 osmdroid contributors.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "PebbleKit 2 (Rebble)",
            "Copyright 2023 Rebble contributors.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "SQLDelight",
            "Copyright 2016 Square, Inc.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "Kermit",
            "Copyright 2021 Touchlab.",
            "Apache License 2.0",
            APACHE_2_0,
        ),
        LibraryLicense(
            "SQLCipher",
            "Copyright (c) 2008-2024 Zetetic LLC",
            "BSD 3-Clause License",
            BSD_LICENSE,
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_label_license)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(libraries) { lib ->
                Column {
                    Text(text = lib.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = lib.copyright, style = MaterialTheme.typography.bodySmall)
                    Text(text = "License: ${lib.licenseName}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lib.licenseText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 10
                        )
                    }
                }
            }
        }
    }
}

private const val APACHE_2_0 = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

private const val BSD_LICENSE = """
Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
"""
