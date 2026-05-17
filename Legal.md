# VellumSync Legal and Compatibility Notes

## License model

VellumSync uses the Apache License, Version 2.0 for repository source code, documentation, scripts, project configuration, and release workflow files unless a file states otherwise.

The full license text is in `LICENSE`. Project attribution and trademark/compatibility notices are in `NOTICE`.

## Compatibility positioning

VellumSync is an independent compatibility-focused Android application. It is not affiliated with, endorsed by, or sponsored by Ratta Supernote, BOOX, Onyx, Android, Google, or any other device/platform owner referenced during testing.

Names such as Supernote, Ratta, BOOX, Onyx, Android, and Google are used only to describe compatibility targets, input file families, device behavior, and testing environments.

## Test files and user data

Private user-created notes, exported PDFs, screenshots, diagnostics JSON files, and local test corpora are not part of the default project license and should not be committed to the public repository.

A test fixture should be committed only when it has explicit permission for public use and includes a clear fixture-specific licensing notice.

## Read-only safety boundary

VellumSync's current Supernote `.note` workflow is read-only. Imported notes are copied into app-owned cache, and local overlay data is stored separately from the original Supernote file.

Apache-2.0 applies to the implementation and documentation. It does not grant any rights to third-party proprietary file formats, trademarks, services, device firmware, or cloud APIs beyond what the law otherwise allows.
