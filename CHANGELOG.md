# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.5] - 2023-11-28

### Added

- added CHANGELOG.md to repository

### Fixed

- changed mock implementation fixing improper service mock injection if mock was used before connecting to non-mocked service  

### Changed

- extended service proxy for test purposes to allow mocking gaze data source
- service proxy allows mocking calibration procedure