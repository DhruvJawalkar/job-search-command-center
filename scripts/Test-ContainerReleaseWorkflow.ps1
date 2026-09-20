Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$workspace = Split-Path $PSScriptRoot -Parent
$workflowPath = Join-Path $workspace '.github/workflows/container-release.yml'
$recoveryWorkflowPath = Join-Path $workspace '.github/workflows/recover-container-release.yml'
$releaseTemplatePath = Join-Path $workspace '.github/release/compose.release.yaml.tmpl'
$connectedReleaseTemplatePath = Join-Path $workspace '.github/release/compose.connected.release.yaml.tmpl'
$documentationPath = Join-Path $workspace 'docs/CONTAINER_RELEASE.md'

function Assert-Match([string]$Content, [string]$Pattern, [string]$Description) {
    if ($Content -notmatch $Pattern) {
        throw "Container release workflow check failed: $Description"
    }
}

function Assert-NotMatch([string]$Content, [string]$Pattern, [string]$Description) {
    if ($Content -match $Pattern) {
        throw "Container release workflow check failed: $Description"
    }
}

if (!(Test-Path -LiteralPath $workflowPath -PathType Leaf)) {
    throw "Missing workflow: $workflowPath"
}
if (!(Test-Path -LiteralPath $recoveryWorkflowPath -PathType Leaf)) {
    throw "Missing protected release recovery workflow: $recoveryWorkflowPath"
}
if (!(Test-Path -LiteralPath $documentationPath -PathType Leaf)) {
    throw "Missing release guidance: $documentationPath"
}
if (!(Test-Path -LiteralPath $releaseTemplatePath -PathType Leaf)) {
    throw "Missing digest-pinned Compose release template: $releaseTemplatePath"
}
if (!(Test-Path -LiteralPath $connectedReleaseTemplatePath -PathType Leaf)) {
    throw "Missing digest-pinned connected Compose release template: $connectedReleaseTemplatePath"
}

$workflow = Get-Content -LiteralPath $workflowPath -Raw
$recoveryWorkflow = Get-Content -LiteralPath $recoveryWorkflowPath -Raw
$releaseTemplate = Get-Content -LiteralPath $releaseTemplatePath -Raw
$connectedReleaseTemplate = Get-Content -LiteralPath $connectedReleaseTemplatePath -Raw
$documentation = Get-Content -LiteralPath $documentationPath -Raw

Assert-Match $workflow '(?ms)^on:\s*.*?push:\s*.*?tags:\s*.*?"v\*\.\*\.\*"' 'version-tag trigger is absent'
Assert-Match $workflow '(?ms)workflow_dispatch:\s*.*?publish:\s*.*?default:\s*false' 'manual validation must default to no publication'
Assert-Match $workflow '(?ms)release_tag:\s*.*?required:\s*false\s*.*?default:\s*""' 'commit-only validation must not require a release tag'
Assert-Match $workflow 'release_version="validation-\$\{GITHUB_SHA:0:12\}"' 'untagged validation is not bound to the exact commit SHA'
Assert-Match $workflow '(?ms)if \[\[ "\$GITHUB_EVENT_NAME" == "push" \]\]; then.*?publish_requested=false' 'a tag push must validate without automatically requesting publication'
Assert-Match $workflow 'Publication requires an existing exact vMAJOR\.MINOR\.PATCH tag' 'publication does not explicitly reject a missing release tag'
Assert-Match $workflow 'RELEASE_VERSION:\s*\$\{\{ needs\.preflight\.outputs\.release_version \}\}' 'pre-publication images do not use the resolved validation version'
Assert-Match $workflow '(?m)^\s+environment:\s+container-release\s*$' 'publish job is not protected by the release environment'
Assert-Match $workflow 'needs\.preflight\.outputs\.publish_requested\s*==\s*''true''' 'publish job lacks an explicit publication request condition'
Assert-Match $workflow 'GITHUB_REF"\s*!=\s*"refs/tags/\$release_tag' 'manual publication is not restricted to a tag ref'
Assert-Match $workflow 'vars\.DOCKERHUB_NAMESPACE' 'configurable Docker Hub namespace is absent'
Assert-Match $workflow 'IMAGE_REPOSITORY_NAME:\s*job-search-command-center' 'the confirmed single Docker Hub repository is absent'
Assert-Match $workflow 'component_tag="\$COMPONENT-\$RELEASE_TAG"' 'component-prefixed tags are absent'
Assert-NotMatch $workflow 'job-search-command-center-(api|portal)' 'obsolete component repository names remain in the workflow'
Assert-Match $workflow "tr '':upper:'' '':lower:''|tr '\[:upper:\]' '\[:lower:\]'" 'namespace is not normalized to lowercase'
Assert-Match $workflow 'secrets\.DOCKERHUB_USERNAME' 'Docker Hub username environment secret is absent'
Assert-Match $workflow 'secrets\.DOCKERHUB_TOKEN' 'Docker Hub token environment secret is absent'
Assert-Match $workflow 'linux/amd64,linux/arm64' 'multi-architecture publication is absent'
Assert-Match $workflow 'component:\s*\[api, portal, broker\]' 'API, portal, and egress broker are not independently handled'
Assert-Match $workflow 'node --test test/\*\.test\.mjs' 'egress-broker tests are absent'
Assert-Match $workflow 'provenance:\s*mode=max' 'maximum BuildKit provenance is absent'
Assert-Match $workflow 'sbom:\s*true' 'BuildKit SBOM attestation is absent'
Assert-Match $workflow 'actions/attest@[0-9a-f]{40}' 'signed GitHub attestations are absent or unpinned'
Assert-Match $workflow 'create-storage-record:\s*false' 'personal-repository attestations must not request organization-only storage records'
Assert-NotMatch $workflow '(?m)^\s+artifact-metadata:\s*write\s*$' 'organization-only artifact metadata permission is unnecessarily enabled'
Assert-Match $workflow 'cosign sign --yes' 'keyless signing is absent'
Assert-Match $workflow 'cosign verify' 'signature verification is absent'
Assert-Match $workflow '--certificate-identity\s+"https://github\.com/\$\{GITHUB_REPOSITORY\}/\.github/workflows/container-release\.yml@refs/tags/\$RELEASE_TAG"' 'signature verification is not bound to the exact tag identity'
Assert-Match $workflow '(?ms)- name:\s*Verify the GitHub provenance attestation\s+env:\s+GH_TOKEN:\s*\$\{\{ github\.token \}\}' 'GitHub attestation verification is not authenticated with the workflow token'
Assert-Match $workflow 'pnpm audit --prod --audit-level high' 'pnpm dependency gate is absent'
Assert-Match $workflow 'resolve node:22\.23\.2-alpine3\.24' 'the patched Node 22 Alpine base is not resolved for release builds'
Assert-Match $workflow 'node-version:\s*"22\.23\.2"' 'the source gate is not using the patched Node 22 runtime'
Assert-Match $workflow 'github/codeql-action/init@[0-9a-f]{40}' 'CodeQL is absent or unpinned'
Assert-Match $workflow 'scanners:\s*secret' 'secret scan is absent'
Assert-Match $workflow 'docker/scout-action@[0-9a-f]{40}' 'Docker Scout gate is absent or unpinned'
Assert-Match $workflow 'Require zero known project-image vulnerabilities in Docker Scout[\s\S]*?only-severities:\s*critical,high,medium,low,unspecified[\s\S]*?exit-code:\s*true' 'Docker Scout does not block every known project-image severity'
Assert-Match $workflow 'Require zero known project-image vulnerabilities in Trivy[\s\S]*?severity:\s*UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL[\s\S]*?ignore-unfixed:\s*false[\s\S]*?exit-code:\s*"1"' 'pre-publication Trivy does not block every known project-image vulnerability'
Assert-Match $workflow 'trivy image --platform linux/amd64 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --exit-code 1' 'published amd64 Trivy gate does not block every known project-image vulnerability'
Assert-Match $workflow 'trivy image --platform linux/arm64 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --exit-code 1' 'published arm64 Trivy gate does not block every known project-image vulnerability'
Assert-Match $workflow 'ignore-unfixed:\s*true' 'fixable vulnerability gate is absent'
Assert-Match $workflow 'NGINX_UPSTREAM:\s*docker\.io/library/nginx:1\.30\.5-alpine3\.24-slim' 'patched official Nginx dependency is not declared'
Assert-Match $workflow 'POSTGRES_UPSTREAM:\s*docker\.io/library/postgres:17\.11-alpine3\.24' 'patched official PostgreSQL dependency is not declared'
Assert-Match $workflow 'dependency:\s*\[nginx, postgres\]' 'official Nginx and PostgreSQL dependencies are not both scanned'
Assert-Match $workflow 'Gate fixable high and critical runtime OS-package vulnerabilities[\s\S]*?vuln-type:\s*os' 'runtime dependency gate is not restricted to fixable OS-package vulnerabilities'
Assert-Match $workflow 'Reject unexpected fixable library findings in runtime dependencies' 'unexpected runtime library finding assertion is absent'
Assert-Match $workflow '\(\$findings \| length\) == 22' 'the reviewed PostgreSQL gosu finding count is not asserted'
Assert-Match $workflow '\.target == "usr/local/bin/gosu"[\s\S]*?\.package == "stdlib"[\s\S]*?\.installed == "v1\.24\.6"' 'the PostgreSQL gosu exception is not narrowly scoped'
Assert-Match $workflow 'LicenseRef-PolyForm-Noncommercial-1\.0\.0' 'source-available license label is absent'
Assert-Match $workflow 'required-notice=Required Notice: Copyright © 2026 Dhruv Jawalkar' 'required creator notice is absent from image labels'
Assert-Match $workflow 'Assemble digest-pinned Compose release bundle' 'release bundle finalization is absent'
Assert-Match $workflow 'Smoke-test the exact published release images' 'published release images are not runtime-smoke-tested'
Assert-Match $workflow 'verify_stack jscc-release-empty.*false 0 0' 'empty-mode published-image smoke test is absent'
Assert-Match $workflow 'verify_stack jscc-release-demo.*true 1 3' 'demo connected-mode published-image smoke test is absent'
Assert-Match $workflow 'Expected 34 successful migrations' 'published-image smoke test does not verify the complete schema'
Assert-Match $workflow 'Attest the verified release bundle' 'the verified Compose bundle is not attested'
Assert-Match $workflow 'job-search-command-center-\$RELEASE_TAG-compose-bundle\.tar\.gz' 'the verified Compose bundle is not packaged with a versioned name'
Assert-Match $workflow 'sha256sum compose\.yaml compose\.connected\.yaml gateway/nginx\.conf CONNECTED_RUNTIME\.md release-manifest\.json' 'connected override or connected-runtime guidance is missing from release checksums'
Assert-Match $workflow 'actions/download-artifact@[0-9a-f]{40}' 'release evidence download is absent or unpinned'

Assert-Match $recoveryWorkflow '(?ms)^on:\s*workflow_dispatch:' 'release recovery must be manually dispatched'
Assert-Match $recoveryWorkflow '(?m)^\s+environment:\s+container-release\s*$' 'release recovery verification is not protected by the release environment'
Assert-Match $recoveryWorkflow 'ref:\s*refs/tags/\$\{\{ inputs\.release_tag \}\}' 'release recovery does not check out the requested immutable tag'
Assert-Match $recoveryWorkflow '\.head_sha == \$sha.*?\.head_branch == \$tag.*?\.conclusion == "failure"' 'release recovery is not bound to the failed publication run and tag commit'
Assert-Match $recoveryWorkflow 'required_success_steps[\s\S]*?Docker Scout fixable severity gate[\s\S]*?Verify the keyless signature identity[\s\S]*?GitHub provenance attestation.*?"failure"' 'release recovery does not prove the original protected jobs reached the narrow expected failure'
Assert-Match $recoveryWorkflow 'name:\s*resolved-release-inputs[\s\S]*?run-id:\s*\$\{\{ inputs\.source_run_id \}\}' 'release recovery does not reuse the original resolved dependency evidence'
Assert-Match $recoveryWorkflow 'GH_TOKEN:\s*\$\{\{ github\.token \}\}[\s\S]*?gh attestation verify' 'release recovery provenance verification is not authenticated'
Assert-Match $recoveryWorkflow '--certificate-identity\s+"https://github\.com/\$\{GITHUB_REPOSITORY\}/\.github/workflows/container-release\.yml@refs/tags/\$RELEASE_TAG"' 'release recovery does not verify the original tag-bound signing identity'
Assert-Match $recoveryWorkflow 'org\.opencontainers\.image\.revision' 'release recovery does not verify the published source revision label'
Assert-Match $recoveryWorkflow 'trivy image --platform linux/amd64 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --exit-code 1' 'release recovery does not block every known amd64 project-image vulnerability'
Assert-Match $recoveryWorkflow 'trivy image --platform linux/arm64 --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL --exit-code 1' 'release recovery does not block every known arm64 project-image vulnerability'
Assert-Match $recoveryWorkflow 'Require zero known project-image vulnerabilities in Docker Scout[\s\S]*?only-severities:\s*critical,high,medium,low,unspecified[\s\S]*?exit-code:\s*true' 'release recovery Docker Scout gate does not block every known project-image severity'
Assert-Match $recoveryWorkflow 'verify_stack jscc-release-empty.*false 0 0' 'release recovery lacks the empty/local-only runtime smoke test'
Assert-Match $recoveryWorkflow 'verify_stack jscc-release-demo.*true 1 3' 'release recovery lacks the demo/connected runtime smoke test'
Assert-Match $recoveryWorkflow 'Expected 34 successful migrations' 'release recovery does not verify the complete schema'
Assert-Match $recoveryWorkflow 'Attest the recovery-verified release bundle' 'release recovery does not attest the accepted bundle'
Assert-NotMatch $recoveryWorkflow 'docker/build-push-action|push:\s*true|cosign sign --yes' 'release recovery must never rebuild, push, or replace published images'

$recoveryUses = [regex]::Matches($recoveryWorkflow, '(?m)^\s*-?\s*uses:\s*([^\s#]+)')
if ($recoveryUses.Count -eq 0) { throw 'Container release workflow check failed: no recovery actions found' }
foreach ($use in $recoveryUses) {
    $reference = $use.Groups[1].Value
    if ($reference -notmatch '@[0-9a-f]{40}$') {
        throw "Container release workflow check failed: recovery action is not pinned to a full commit SHA: $reference"
    }
}

$uses = [regex]::Matches($workflow, '(?m)^\s*-?\s*uses:\s*([^\s#]+)')
if ($uses.Count -eq 0) { throw 'Container release workflow check failed: no actions found' }
foreach ($use in $uses) {
    $reference = $use.Groups[1].Value
    if ($reference -notmatch '@[0-9a-f]{40}$') {
        throw "Container release workflow check failed: action is not pinned to a full commit SHA: $reference"
    }
}

Assert-NotMatch $workflow '(?m)^\s*build-args:\s*\$\{\{\s*secrets\.' 'a secret is passed directly as Docker build arguments'
foreach ($placeholder in @('__API_IMAGE__', '__PORTAL_IMAGE__', '__NGINX_IMAGE__', '__POSTGRES_IMAGE__')) {
    Assert-Match $releaseTemplate ([regex]::Escape($placeholder)) "release Compose template is missing $placeholder"
}
Assert-Match $connectedReleaseTemplate '__BROKER_IMAGE__' 'connected release Compose template is missing __BROKER_IMAGE__'
Assert-Match $workflow '"\$api_image" "\$portal_image" "\$broker_image" "\$NGINX_IMAGE" "\$POSTGRES_IMAGE"' 'release bundle does not reject non-digest broker references alongside the base images'
Assert-NotMatch $releaseTemplate '(?m)^\s+build:' 'release Compose template contains a source build context'
Assert-NotMatch $connectedReleaseTemplate '(?m)^\s+build:' 'connected release Compose template contains a source build context'
Assert-Match $connectedReleaseTemplate 'APP_CONNECTED_ENABLED:\s*"true"' 'connected release override does not explicitly enable connected mode'
Assert-Match $connectedReleaseTemplate 'OPENAI_API_KEY' 'connected release override does not isolate the provider credential in the broker'
Assert-Match $releaseTemplate 'APP_WORKSPACE_ROOT:\s*/workspace' 'release API workspace root is not fixed to the mounted workspace'
Assert-Match $releaseTemplate 'APP_PREPARATION_WORKSPACE_FOLDER:\s*/workspace/preparation-workspace' 'release preparation inventory path is not fixed to its mounted folder'
Assert-Match $releaseTemplate '/tmp:size=32m,mode=1777' 'release gateway tmpfs is too small for the supported request limit'
Assert-Match (Get-Content -LiteralPath (Join-Path $workspace 'gateway/nginx.conf') -Raw) 'client_max_body_size\s+16m;' 'gateway upload request limit is not preserved'
Assert-Match $documentation 'Those images and their source tag are not an accepted release and must be removed before the version is reused' 'guidance does not reject the vulnerable publication candidate'
Assert-Match $documentation 'PolyForm Noncommercial License 1\.0\.0' 'license limitations are not explained'
Assert-Match $documentation 'required reviewers' 'manual environment approval is not documented'

Write-Output 'Container release workflow structural checks passed. No remote workflow or publication was performed.'
