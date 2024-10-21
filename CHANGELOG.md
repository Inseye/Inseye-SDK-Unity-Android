# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - 2024-10-21

### Changed

- updated shared library to version `0.0.2` and adjusted code in project accordingly

## [0.0.7] - 2024-04-18

### Changed

- updated shared module

### Fixed

- updated Shared module, fixing missing permission issue

- all leftover logs moved to `com.inseye.unitysdk.Log`

## [0.0.6] - 2024-01-24

### Changed

- frequent status checks in `CalibrationProcedure` to prevent accidental exception throws
 
- migrated to sdk 34

- AGP migrated to version 8.2.0

- migrated BuildConfig to gradle build files

## [0.0.5] - 2023-11-28

### Added

- added CHANGELOG.md to repository

### Fixed

- changed mock implementation fixing improper service mock injection if mock was used before connecting to non-mocked service  

### Changed

- extended service proxy for test purposes to allow mocking gaze data source
- service proxy allows mocking calibration procedure