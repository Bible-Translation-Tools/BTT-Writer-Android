
# Change log

## [1.1.2 (build 8)](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/releases)

- Reverted back to API 28 to temporarily fix issues with newer devices.

## [1.1.1 (build 7)](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/releases)

- Updated Android SDK target to 29 for Google Play Store compatibility.

## [1.1.0 (build 6)](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/releases)
The following changes are ported from [BTT Writer Desktop](https://github.com/Bible-Translation-Tools/BTT-Writer-Desktop)

#### ADDED:
- Show translationWords content for languages other than English - PR [#15](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/pull/15)

#### FIXED:
- Access token issue: no longer need to manually delete token to login or upload - PR [#10](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/pull/10)
 
#### UPDATED:
- Upload success: Link navigates to rendered reader - Issue [#11](https://github.com/Bible-Translation-Tools/BTT-Writer-Android/issues/11)
- Token name now looks like "**btt-writer-android__[YOUR DEVICE NAME]\_[PLATFORM]_[SOME ID]**"
- License, Translation Guidelines & Statement of Faith.

### Security:
 - Please remove the old token named: __"ts-android"__ on WACS
