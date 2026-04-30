# Kill any running Speakeater image-gen scripts so we can restart with new config.
$procs = Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
  Where-Object { $_.CommandLine -like '*generate_speakeater*' -or $_.CommandLine -like '*generate_brimm_images*' -or $_.CommandLine -like '*generate_recipe_images*' }

if ($procs) {
  $procs | ForEach-Object {
    Write-Host "Killing PID $($_.ProcessId)"
    Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
  }
  Start-Sleep 2
} else {
  Write-Host "No gen processes running."
}

Write-Host ""
Write-Host "=== remaining gen processes ==="
Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
  Where-Object { $_.CommandLine -like '*generate_*' } |
  Select-Object ProcessId, @{Name='Script';Expression={
    if ($_.CommandLine -like '*v3*') {'v3_icons'}
    elseif ($_.CommandLine -like '*wordmark*') {'wordmark'}
    elseif ($_.CommandLine -like '*brand_kit*') {'brand_kit'}
    elseif ($_.CommandLine -like '*brimm_images*') {'ingredients'}
    elseif ($_.CommandLine -like '*recipe_images*') {'recipes'}
    else {'other'}
  }} | Format-Table -AutoSize
