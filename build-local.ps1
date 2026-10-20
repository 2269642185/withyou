param([switch]$Test)
$ErrorActionPreference = 'Stop'
$studioJdk = 'D:\software\Android Studio\jbr'
if (-not $env:JAVA_HOME -or $env:JAVA_HOME -eq 'D:\Java\jdk-11') {
    if (Test-Path "$studioJdk\bin\java.exe") { $env:JAVA_HOME = $studioJdk }
}
Push-Location $PSScriptRoot
try {
    if ($Test) { & .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest '-PvalidationApplicationId=com.withyou.anniversary.validation' '-Pandroid.testInstrumentationRunnerArguments.notClass=com.withyou.anniversary.WidgetDesktopEvidenceTest,com.withyou.anniversary.UpdateInstallEvidenceTest,com.withyou.anniversary.TodoCloudEvidenceTest,com.withyou.anniversary.SharedCloudEvidenceTest,com.withyou.anniversary.EmailCloudEvidenceTest' }
    if ($LASTEXITCODE -ne 0 -and $Test) { throw '独立测试副本验证失败，请检查输出。' }
    & .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Gradle 构建失败，请检查输出。' }
} finally { Pop-Location }
