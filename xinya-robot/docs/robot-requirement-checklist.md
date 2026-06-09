# Robot Requirement Checklist

This file checks the current `xinya-robot` codebase against the client requirement set for the temi robot second-stage development.

Status legend:

- `Implemented`: there is working code directly covering the requirement.
- `Partial`: there is only a fragment, placeholder, or adjacent capability.
- `Missing`: no meaningful implementation was found in the current app code.

## 1. System and hardware base capability

### 1.1 App lock / kiosk

- Requirement: boot into custom app, hide temi desktop/app list/back flow/status area, keep app in front.
- Status: `Partial`
- Evidence:
  - Manifest declares temi skill and kiosk metadata.
  - `BootReceiver` exists for auto-start.
  - `KioskManager` can request kiosk mode and hide top bar.
- Gap:
  - `KioskManager` is not clearly wired into `MainActivity`.
  - No crash auto-restart or persistent foreground guard was found.

### 1.2 Hardware status monitoring

- Requirement: serial number, battery, online/offline, WiFi, signal, device running state, backend upload.
- Status: `Missing`
- Gap:
  - No device-status listener/service/reporting implementation found.
  - No backend API for robot status reporting found in this app.

### 1.3 Remote reboot / sleep / wake

- Status: `Missing`
- Gap:
  - No callable service or API bridge found for reboot, sleep, wake.

### 1.4 Hospital environment adaptation

- Requirement: adjustable volume/brightness, slow movement mode, obstacle avoidance.
- Status: `Missing`
- Gap:
  - No volume/brightness control service found.
  - No movement-speed policy or obstacle handling logic found.

### 1.5 Microphone / camera permission control

- Status: `Missing`
- Gap:
  - Audio permission is declared.
  - No camera permission declaration, no camera control service, no runtime toggle flow.

### 1.6 Automatic maintenance and alerting

- Requirement: low battery auto return, collision/path/sensor/offline alerts.
- Status: `Missing`
- Gap:
  - No low-battery handling, charging navigation, or alert upload logic found.

## 2. Map and remote movement

### 2.1 Map and point management

- Status: `Missing`
- Gap:
  - No map save/load abstraction.
  - No point CRUD service.

### 2.2 Remote movement and posture control

- Status: `Missing`
- Gap:
  - No `goto`, no posture rotation/head tilt interface, no remote command executor found.

### 2.3 Movement status callback

- Status: `Missing`
- Gap:
  - No movement-state listener, no progress reporting, no backend upload path found.

## 3. Timed reminder module

### 3.1 Reminder types

- Requirement: wake-up, medication, daily care reminders.
- Status: `Missing`

### 3.2 Execution rules

- Requirement: backend-configured schedule, period, copy, point, repeat count, interval, execution log upload.
- Status: `Missing`

### Related note

- `SyncManager` exists, but it is generic data sync infrastructure, not reminder scheduling.

## 4. Voice PRO data collection

### 4.1 Supported data dimensions

- Requirement: temperature, weight, water intake, diet, stool, urine, pain, symptoms, extensible.
- Status: `Partial`
- Evidence:
  - Existing patient-app PRO API and UI flow are present.
- Gap:
  - Current PRO implementation is questionnaire-style submit via `ProRepository`.
  - No robot-side structured spoken slot parsing for those health metrics was found.

### 4.2 Standard spoken workflow

- Requirement: wakeup, spoken input, ASR parse, on-screen confirm, correction, submit, sync.
- Status: `Partial`
- Evidence:
  - Speech abstraction interfaces exist.
  - Voice session state model exists.
  - iFlytek assets and SDK packages are integrated.
- Gap:
  - No concrete wakeup/ASR/TTS implementation class.
  - No spoken metric parser.
  - No voice-first PRO orchestration service.

### 4.3 Scheduled proactive collection

- Status: `Missing`

## 5. Voice education module

### 5.1 Free-form spoken Q&A education

- Status: `Partial`
- Evidence:
  - Existing education content API and screens are present.
  - Existing agent chat screen can use temi ASR listener.
- Gap:
  - This is not yet a dedicated robot education orchestrator.
  - No clear mapping from spoken nursing questions to CMS education payload plus TTS playback.

### 5.2 Timed automatic bedside education

- Status: `Missing`

### 5.3 Server-triggered temporary education

- Status: `Missing`

## 6. Robot voice control

- Requirement: wakeup, sleep, volume, brightness, go bedside, return dock/standby, start/stop education, confirm data entry.
- Status: `Partial`
- Evidence:
  - `VoiceInteractionConfig` defines wake words, emergency words, clinician keywords, and continuous-dialog window.
  - `VoiceSessionManager` tracks wakeup and intent state.
- Gap:
  - Keywords are config only.
  - No actual command dispatcher to robot device actions.
  - No movement/device-control integration.

## 7. Server-side intelligent linkage tasks

- Requirement: e.g. bed-leave event triggers camera on/off.
- Status: `Missing`
- Gap:
  - No camera linkage, no event subscription, no temporary task executor found.

## 8. Backend management support from robot app side

### 8.1 Device management support

- Status: `Missing`
- Gap:
  - No status reporting API or push model for serial number, online status, power, network, position, exceptions.

### 8.2 Task management support

- Status: `Missing`
- Gap:
  - No reminder-task or education-task execution engine.

### 8.3 Data management support

- Status: `Partial`
- Evidence:
  - PRO, education progress, and generic sync APIs exist.
  - Local DB entities and sync queue exist.
- Gap:
  - `SyncWorker` is unfinished and does not perform actual background sync yet.
  - No robot operation audit trail or device action log service found.

## 9. Non-functional requirements

### 9.1 Boot auto-start and resident app

- Status: `Partial`
- Evidence:
  - Boot receiver exists.
- Gap:
  - No app self-recovery or crash restart logic found.

### 9.2 Stable movement and obstacle avoidance

- Status: `Missing`

### 9.3 Voice recognition accuracy adaptation

- Status: `Partial`
- Evidence:
  - Offline SDK packages and resource-copy helper are integrated.
- Gap:
  - No actual runtime tuning/implementation class to validate accuracy.

### 9.4 Compliance and privacy

- Status: `Partial`
- Evidence:
  - No audio/video call feature found.
- Gap:
  - No explicit audit log service, encrypted local data strategy, or privacy-control flow found in app code.

## 10. Overall conclusion

Current implementation is best described as:

- `Implemented foundation`
  - temi app shell
  - basic binding/session
  - kiosk metadata and helper
  - patient-side PRO/education/network/data models
  - offline speech abstraction layer
  - iFlytek resource packaging
- `Not yet implemented as robot product capability`
  - concrete offline ASR/TTS/wakeup engines
  - robot command dispatch
  - navigation and movement
  - timed reminders
  - robot-side device status reporting
  - camera/microphone runtime control
  - server-triggered robot task orchestration

## 11. Practical conclusion for your ownership

If your responsibility is "voice capability only", then the codebase currently shows:

- You have already prepared:
  - SDK package integration
  - resource copy helper
  - session state model
  - wake-word and keyword configuration
  - interface contracts for wakeup, command words, IAT, and TTS
- You have not yet completed:
  - concrete iFlytek engine implementation
  - end-to-end voice orchestration
  - callable facade for teammate integration
  - spoken PRO structured parsing
  - voice command to robot action mapping

So the answer to "是不是都实现了" is:

- No.
- Only the foundation and part of the voice architecture are present.
- The full client requirement set is far from complete in the current repository.
