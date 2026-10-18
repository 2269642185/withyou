# AndroidX ExifInterface 1.3.7

Unmodified official Google Maven AAR, bundled to avoid an observed Java/Google Maven TLS download interruption on the delivery machine. License: Apache-2.0; see the included LICENSE-2.0.txt, NOTICE.txt and official POM license metadata.

Official artifact: https://dl.google.com/dl/android/maven2/androidx/exifinterface/exifinterface/1.3.7/exifinterface-1.3.7.aar

SHA-1 (verified against the official .aar.sha1): `294ecda0567595907e142b6c364375521ba2b876`

SHA-256: `0e8f1832266c5b0667ad3d3b1098e624e49a09075493a014a7e88af01fd30ad3`

The app already includes AndroidX annotations transitively through its Compose/activity dependencies. No other EXIF runtime dependency is required. To use normal Maven resolution on another environment, replace `implementation(files("libs/exifinterface-1.3.7.aar"))` with `implementation("androidx.exifinterface:exifinterface:1.3.7")`.
