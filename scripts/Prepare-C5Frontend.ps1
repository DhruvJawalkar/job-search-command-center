Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$source=Join-Path $root 'frontend'
$target=Join-Path $root 'tmp/c5-ui'
New-Item -ItemType Directory -Path $target -Force | Out-Null
# Copy source only, never .env files, live build output or browser storage.
foreach($folder in @('app','public','worker','.openai')){
 if(Test-Path (Join-Path $source $folder)){Copy-Item -LiteralPath (Join-Path $source $folder) -Destination $target -Recurse -Force}
}
foreach($file in @('package.json','pnpm-lock.yaml','vite.config.ts','next.config.ts','tsconfig.json','postcss.config.mjs')){
 if(Test-Path (Join-Path $source $file)){Copy-Item -LiteralPath (Join-Path $source $file) -Destination $target -Force}
}
$modules=Join-Path $target 'node_modules'
if((Test-Path $modules) -and (Get-Item $modules).LinkType -eq 'Junction'){
 # Delete only our junction, never recurse into the shared installed dependencies.
 [IO.Directory]::Delete($modules)
}
New-Item -ItemType Directory -Path $modules -Force | Out-Null
$manifest=Get-Content (Join-Path $source 'package.json') -Raw | ConvertFrom-Json
foreach($dependency in @($manifest.dependencies.PSObject.Properties.Name)+@($manifest.devDependencies.PSObject.Properties.Name)){
 $installed=Get-Item (Join-Path (Join-Path $source 'node_modules') $dependency)
 $resolved=$installed.ResolveLinkTarget($true)
 $path=Join-Path $modules $dependency
 New-Item -ItemType Directory -Path (Split-Path $path -Parent) -Force | Out-Null
 if(!(Test-Path $path)){New-Item -ItemType Junction -Path $path -Target $(if($resolved){$resolved.FullName}else{$installed.FullName}) | Out-Null}
}
Write-Output "Prepared isolated frontend source: $target"
