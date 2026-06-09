# Robot Interface List

For detailed offline speech integration, command configuration, and usage examples, see:

`docs/offline-voice-api-readme.md`

## Scope

This list focuses on robot-side capabilities that are already present as code entry points in `xinya-robot`, especially the speech, kiosk, binding, and sync pieces that other teammates may call.

## 1. Offline speech abstractions

File: `app/src/main/java/com/xinya/dtx/core/speech/OfflineSpeechEngines.kt`

- `WakeupEngine`
  - `val events: Flow<WakeupEvent>`
  - `suspend fun start()`
  - `suspend fun stop()`
- `CommandWordEngine`
  - `suspend fun updateGrammar(commands: List<CommandPhrase>)`
  - `suspend fun startListening()`
  - `suspend fun stopListening()`
  - `val results: Flow<CommandRecognitionResult>`
- `OfflineIatEngine`
  - `suspend fun startListening()`
  - `suspend fun stopListening()`
  - `val results: Flow<IatRecognitionResult>`
- `OfflineTtsEngine`
  - `suspend fun speak(text: String, interruptCurrent: Boolean = false)`
  - `suspend fun stop()`

Related models:

- `WakeupEvent`
- `CommandPhrase`
- `CommandRecognitionResult`
- `IatRecognitionResult`

Status:

- Interface layer and concrete iFlytek implementations exist.
- AIKit wakeup, AIKit command-word recognition, AIKit offline TTS, and MSC offline IAT have been verified on a real device/robot.

## 2. iFlytek resource preparation

File: `app/src/main/java/com/xinya/dtx/core/speech/IflytekResourcePreparer.kt`

- `fun prepareAikitResources(): File`
  - Copies `assets/iflytek/aikit` into the app work directory.
- `fun getAikitWorkDir(): File`
  - Returns the runtime work directory under app external files or internal files.

Status:

- Implemented.
- AIKit runtime resources are copied into the app-scoped work directory.
- MSC/IAT resources are read directly from packaged assets and do not require the same runtime copy flow.

## 3. Voice session and interaction state

Files:

- `app/src/main/java/com/xinya/dtx/core/voice/VoiceSessionModels.kt`
- `app/src/main/java/com/xinya/dtx/core/voice/VoiceSessionManager.kt`
- `app/src/main/java/com/xinya/dtx/core/voice/VoiceInteractionConfig.kt`
- `app/src/main/java/com/xinya/dtx/di/VoiceModule.kt`

Exposed types:

- `VoiceSessionState`
- `VoicePriority`
- `VoiceSpeakerRole`
- `VoiceIntent`
- `VoiceSessionSnapshot`
- `VoiceInteractionConfig`

Manager API:

- `val snapshot: StateFlow<VoiceSessionSnapshot>`
- `fun onWakeup(keyword: String)`
- `fun onIntentHandled(intent: VoiceIntent)`
- `fun onEmergencyTriggered(intent: VoiceIntent)`
- `fun resetToStandby()`
- `fun refreshTimeout()`
- `fun isContinuousDialogActive(): Boolean`
- `fun isNightMode(): Boolean`

Status:

- Implemented as state-management logic.
- Basic intent parsing and dispatch infrastructure exists.
- Wakeup-to-dialog orchestration, timeout switching, emergency recognition, and event output are wired through `VoiceInteractionService`.
- Business action execution remains the caller's responsibility.

## 3.1 Product-facing offline voice orchestration

File: `app/src/main/java/com/xinya/dtx/core/voice/VoiceInteractionService.kt`

API:

- `val events: SharedFlow<VoiceInteractionEvent>`
- `val state: StateFlow<VoiceSessionSnapshot>`
- `suspend fun start()`
- `suspend fun stop()`
- `suspend fun updateDialogCommands(commands: List<CommandPhrase>)`
- `suspend fun speak(text: String, interruptCurrent: Boolean = true)`

Implemented orchestration:

- Standby wake-word, emergency-term, and clinician-command recognition.
- Automatic restart of the AIKit command-recognition window.
- Wakeup feedback and switch to MSC continuous dialog.
- 60-second dialog timeout and automatic return to standby.
- Emergency TTS interruption and timestamped emergency event.
- Night-mode filtering and priority-ordered multi-command events.

Boundary:

- Callers handle nurse-station alerts, robot/device actions, page navigation, and persistence.

## 4. temi kiosk and app boot

Files:

- `app/src/main/java/com/xinya/dtx/core/kiosk/KioskManager.kt`
- `app/src/main/java/com/xinya/dtx/core/kiosk/BootReceiver.kt`

Kiosk API:

- `fun enableKioskMode(activity: Activity)`
- `fun disableKioskMode(activity: Activity)`
- `fun isKioskModeActive(): Boolean`
- `fun handleBackPress(): Boolean`

Boot behavior:

- `BootReceiver` listens to `BOOT_COMPLETED`
- Starts `MainActivity`

Status:

- Basic kiosk helper exists.
- Current `MainActivity` does not visibly inject/call `KioskManager`.
- App auto-start receiver exists.

## 5. Robot binding and session

Files:

- `app/src/main/java/com/xinya/dtx/core/network/ApiService.kt`
- `app/src/main/java/com/xinya/dtx/feature/setup/ui/SetupViewModel.kt`
- `app/src/main/java/com/xinya/dtx/core/session/SessionManager.kt`

Backend API:

- `suspend fun bindRobot(request: RobotBindRequest): Response<ApiResponse<RobotAuthResponse>>`

Session API:

- `val patientId: Flow<String>`
- `val patientName: Flow<String>`
- `val isBound: Flow<Boolean>`
- `val deviceId: Flow<String>`
- `suspend fun saveBindingInfo(patientId: String, patientName: String, deviceToken: String)`
- `suspend fun savePatientInfo(patientId: String, patientName: String)`
- `suspend fun ensureDeviceId(): String`
- `suspend fun clearSession()`

Setup entry:

- `fun bind(patientId: String, bindCode: String)`
- `fun clearError()`

Status:

- Implemented for device binding and local session persistence.

## 6. Existing backend-facing business APIs already in app

File: `app/src/main/java/com/xinya/dtx/core/network/ApiService.kt`

Already defined:

- Patient
  - `createPatient`
  - `getPatient`
- Agent
  - `chat`
  - `getRecommendedQuestions`
- Clinical
  - `getCurrentStage`
  - `transitionStage`
- PRO
  - `getProQuestions`
  - `submitPro`
- HopeTree
  - `getHopeTreeStatus`
  - `growHopeTree`
- Education
  - `getEducationContents`
  - `getEducationContent`
  - `reportProgress`
- Sync
  - `syncBatch`

Status:

- These are patient-app/business APIs.
- They are not yet a robot orchestration API set for movement, reminder scheduling, device status, camera control, or remote operations.

## 7. Sync capability

File: `app/src/main/java/com/xinya/dtx/core/sync/SyncManager.kt`

Available methods:

- `fun initializePeriodicSync()`
- `fun triggerImmediateSync()`
- `suspend fun enqueue(tableName: String, recordId: Long, operation: String, payload: Any)`
- `fun observePendingCount(): Flow<Int>`
- `suspend fun performSync(): SyncResult`

Models:

- `SyncResult`

Status:

- Queueing and batch sync logic exists.
- `SyncWorker` still contains `TODO` and currently returns `Result.success()` without calling `performSync()`.

## 8. Important missing robot-facing interfaces

The following interface groups do not exist yet as stable callable APIs in the current project:

- Robot device status service
  - serial number
  - battery
  - online/offline
  - wifi/signal
- Robot remote operation service
  - reboot
  - sleep
  - wake
  - volume
  - brightness
- Navigation service
  - map/point management
  - goto target point
  - movement state callbacks
  - head/body posture control
- Reminder task service
  - timed task registration
  - task execution callbacks
  - task log reporting
- Camera/microphone permission control service
- Voice PRO structured collection service
- Voice education orchestration service
- A single facade for teammates to call, such as `RobotCapabilityService` or `RobotSpeechFacade`

## Suggested next packaging step

If this module is meant to be handed to another teammate for UI integration, the next best step is to expose a unified facade like:

- `RobotSpeechFacade`
  - `initialize()`
  - `startWakeup()`
  - `startProCollection()`
  - `stopListening()`
  - `speak(text)`
  - `observeState()`
- `RobotDeviceFacade`
  - `getStatus()`
  - `setVolume(level)`
  - `setBrightness(level)`
  - `sleep()`
  - `wake()`
- `RobotNavigationFacade`
  - `goTo(pointId)`
  - `stopMovement()`
  - `observeMovementState()`
