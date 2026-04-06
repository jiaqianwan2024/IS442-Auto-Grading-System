# IS442 AUTO-GRADING SYSTEM
## Component Reference Documentation

**Purpose:** Complete reference guide for all components in the system

---

## 📋 TABLE OF CONTENTS

1. [Legend](#legend)
2. [Core Layer](#core-layer)
3. [Web Layer](#web-layer)
4. [Extraction Layer](#extraction-layer)
5. [Discovery Layer](#discovery-layer)
6. [Test Case Generator Layer](#test-case-generator-layer)
7. [Execution Layer](#execution-layer)
8. [Analysis Layer](#analysis-layer)
9. [Penalty Layer](#penalty-layer)
10. [Plagiarism Layer](#plagiarism-layer)
11. [Multi-Assessment Layer](#multi-assessment-layer)
12. [Shared Models](#shared-models)

---

## 📖 LEGEND

- 🎯 **Controller** - Service brain/orchestrator
- ⚙️ **Service** - Business logic implementation
- 📦 **Model** - Data structure/object
- 🔧 **Utility** - Static helper methods
- ⚡ **Configuration** - Spring configuration class

---

## 🔧 CORE LAYER

**Package:** `com.autogradingsystem`
**Purpose:** Application foundation and shared utilities

---

### Main.java
**Type:** Entry Point
**Package:** `com.autogradingsystem`

**Purpose:** Spring Boot application launcher.

```java
public static void main(String[] args)
```
- Starts Spring Boot, scans for beans, activates profile (web or cli)

**Dependencies:** `SpringApplication`, `@SpringBootApplication`

---

## 🌐 WEB LAYER

**Package:** `com.autogradingsystem.web`
**Purpose:** HTTP layer for browser-based interface

---

### HomeController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.web.controller`

**Purpose:** Serves the main UI page.

```java
@GetMapping("/")
public String home()
```
- Returns the Thymeleaf view name `"index"`, which resolves to `src/main/resources/templates/index.html`

**Dependencies:** none

---

### PenaltyRestController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.web.controller`

**Purpose:** REST API endpoints for the penalty microservice. Allows external callers to apply penalty strategies to grading results.

**Key Routes:**

```java
@GetMapping("/api/penalty/health")
public ResponseEntity<Map<String, String>> health()
```
- Returns `{ status: "UP", service: "penalty-microservice", strategies: "2" }`

```java
@PostMapping("/api/penalty/single")
public ResponseEntity<ProcessedScore> calculateSinglePenalty(@RequestBody PenaltyGradingResult result)
```
- Applies all registered strategy-based penalties to one grading result

```java
@PostMapping("/api/penalty/student/{studentId}")
public ResponseEntity<ProcessedScore> calculateStudentPenalties(
    @PathVariable String studentId,
    @RequestBody List<PenaltyGradingResult> results)
```
- Applies per-question penalties + global CSV deductions (`config/penalties.csv`) for one student

```java
@PostMapping("/api/penalty/student/{studentId}/custom")
public ResponseEntity<ProcessedScore> calculateStudentPenaltiesWithCustomCsv(
    @PathVariable String studentId,
    @RequestBody List<PenaltyGradingResult> results,
    @RequestParam String penaltiesCsvPath)
```
- Same as above but uses a caller-specified CSV path

**Dependencies:** `PenaltyController`

---

### TestCaseReviewController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.web.controller`

**Purpose:** Handles the 3-step examiner review workflow: parse marks → generate testers → save testers. Constructed with explicit path arguments by `UnifiedAssessmentController` — not a Spring-managed bean.

**Constructor:**
```java
public TestCaseReviewController(Path inputTesters, Path inputTemplate, Path inputExam)
```
- All methods resolve files relative to the injected paths
- Private helpers: `resolveInputTesters()`, `resolveInputTemplate()`, `resolveInputExam()`, `findTemplateZip()`, `findExamPdf()`

**Key Methods:**

```java
public ResponseEntity<Map<String, Object>> parseExamMarks()
```
- Calls `ExamPaperParser.extractMarkWeights()` — LLM reads exam PDF and returns question marks
- Returns `{ success, hasPdf, pdfName, marks: {Q1a:3,...}, totalMarks, message }`
- Returns HTTP 400 if no exam PDF found in `inputExam`

```java
public ResponseEntity<Map<String, Object>> generateTesters(@RequestBody Map<String,Object> body)
```
- Accepts `{ marks: {Q1a:3, Q1b:2, ...} }` from frontend (examiner-confirmed marks)
- Detects script/classpath questions via `ExamPaperParser.extractScriptQuestions()` and routes those to `ScriptTesterGenerator`
- Injects per-question descriptions from `ExamPaperParser.extractQuestionDescriptions()` into each `QuestionSpec`
- Returns `{ success, testers: { Q1a: { filename, source } } }` — does **not** write files yet
- If a script question already has a saved tester on disk, loads and returns that instead of generating

```java
public ResponseEntity<Map<String, Object>> saveTesters(@RequestBody Map<String,Object> body)
```
- Accepts `{ testers: { Q1a: "...java source..." } }` — examiner's approved source
- Writes each source to `inputTesters/{qId}Tester.java`
- Returns `{ success, saved: ["Q1aTester.java", ...] }`

**Dependencies:** `ExamPaperParser`, `TemplateTestSpecBuilder`, `LLMTestOracle`, `TesterGenerator`, `ScriptTesterGenerator`

---

### GradingService.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.web.service`

**Purpose:** Bridge between web layer and backend pipeline. Orchestrates all pipeline phases for one assessment, including optional penalty application.

**Constructor:**
```java
public GradingService(AssessmentPathConfig paths)
```
- Extracts individual path fields from `paths` and stores them for use in `runFullPipeline()`
- There is no no-arg constructor in the current implementation

```java
public GradingReport runFullPipeline(String assessmentName, boolean applyPenalties)
```
- `assessmentName` is the sanitised assessment key used for `AssessmentProgressRegistry` updates
- `applyPenalties` controls whether penalty phase runs
- **Workflow:**
  1. Validate — check scoresheet and submissions dir exist
  2. `ExtractionController.extractAndValidate()` — unzip + 4-layer identity resolution
  3. **Guard check** — `savedTestersExist()`: if any `*Tester.java` exists, skip to Phase 4
  3a. *(only if no saved testers)* `TestCaseGeneratorController.generateIfNeeded(specs, weights)`
  4. `DiscoveryController.buildGradingPlan()`
  5. `ExecutionController.gradeAllStudents(gradingPlan, progressCallback)`
  5b. *(only if `applyPenalties` is `true`)* `runPenaltyPhase(results, remarks, allStudents)` → `Map<String, ProcessedScore>`
  6. `PlagiarismController.runPlagiarismCheck(gradingPlan)` → `buildPlagiarismNotes(plagSummary)`
  7. `AnalysisController.analyzeAndDisplayWithPenalties(results, remarks, anomalyRemarks, allStudents, plagiarismNotes, penaltyResults)`
  8. Return `GradingReport`

> **Note:** When `applyPenalties` is `false`, an empty map is passed as `penaltyResults` and no penalty columns appear in the reports.

```java
private Map<String, ProcessedScore> runPenaltyPhase(
    List<GradingResult> results,
    Map<String, String> remarks,
    List<Student> allStudents)
```
- Groups results by student via `ScoreAnalyzer.groupByStudent()`
- For each student, builds `PenaltyGradingResult` objects from:
  - `GradingResult.getQuestionId()` → `questionId`
  - `GradingResult.getScore()` → `rawScore`
  - `ScoreAnalyzer.getMaxScoreFromTester(questionId, inputTesters)` → `maxPossibleScore`
  - `Student.isFolderRenamed()` → `rootFolderCorrect`
  - `!Student.isAnomaly()` → `properHierarchy`
  - `Student.getMissingHeaderFiles().isEmpty()` → `hasHeaders`
  - whether question had `WrongPackage` remark → `hasWrongPackage`
- Calls `PenaltyController.processStudentResults(studentId, penaltyInputs)` per student
- If `config/penalties.csv` exists (sibling of scoresheet), uses the CSV-aware overload
- Falls back to zero deduction on any exception per student
- Prints console summary of penalised students
- **Returns:** `Map<String, ProcessedScore>` keyed by studentId

```java
private boolean savedTestersExist()
```
- Returns `true` when at least one `*Tester.java` file is present in `inputTesters`
- When `true`, Phase 3 is skipped

```java
private Map<String, String> buildPlagiarismNotes(PlagiarismController.PlagiarismSummary plagSummary)
```
- Converts `PlagiarismSummary.flaggedResults` into a per-student readable note
- Format: `"Q1a: flagged with ping.lee.2023 (87.3%); Q2b: flagged with tan.jun.2024 (91.0%)"`
- Only flagged students appear in the map; clean students have no entry

**Progress reporting:**
- `GradingService` calls `AssessmentProgressRegistry.updatePercent()` at each phase boundary
- Passes a `BiConsumer<Integer, Integer>` progress callback to `ExecutionController.gradeAllStudents()` for fine-grained per-task progress updates within Phase 5

**Inner Class: GradingReport**
```java
public static class GradingReport {
    private boolean success;
    private int studentCount;
    private List<GradingResult> results;
    private List<String> logs;

    public GradingReport()
    public GradingReport(boolean success, int studentCount, List<GradingResult> results, List<String> logs)
    public void addLog(String message)
    public boolean isSuccess() / public void setSuccess(boolean success)
    public int getStudentCount() / public void setStudentCount(int studentCount)
    public List<GradingResult> getResults() / public void setResults(List<GradingResult> results)
    public List<String> getLogs()
}
```

**Dependencies:** `ExtractionController`, `DiscoveryController`, `ExecutionController`, `PlagiarismController`, `AnalysisController`, `PenaltyController`, `TestCaseGeneratorController`, `ScoreAnalyzer`, `AssessmentPathConfig`, `AssessmentProgressRegistry`

---

### AssessmentProgressRegistry.java ⚙️
**Type:** Service (static / thread-safe)
**Package:** `com.autogradingsystem.web.service`

**Purpose:** Thread-safe in-memory store for real-time grading progress per assessment. Polled by the browser via `GET /assessments/{name}/progress`. All methods are `static` — no instantiation required (private constructor).

**Key Methods:**

```java
public static void start(String assessment)
```
- Initialises a `ProgressState` entry with `percent=0`, `stage="Starting"`, and records `startedAtMs`

```java
public static void updatePercent(String assessment, int percent, String stage, String message)
```
- Sets an absolute percent value and human-readable stage/message
- Clamps percent to [0, 100]

```java
public static void updateProgress(String assessment, String stage, int percentBase,
                                  int completedUnits, int totalUnits, String message)
```
- Calculates a proportional increment within a 30-point range above `percentBase`
- Used by `GradingService` for fine-grained per-task updates during Phase 5

```java
public static void complete(String assessment, String message)
```
- Sets `percent=100`, `done=true`, `success=true`

```java
public static void fail(String assessment, String message)
```
- Sets `done=true`, `success=false` without setting percent to 100

```java
public static Map<String, Object> snapshot(String assessment)
```
- Returns `{ known, percent, stage, message, done, success, etaSeconds }`
- `etaSeconds` is estimated from elapsed time and remaining fraction

```java
public static void clear(String assessment)
public static void clearAll()
```
- Removes one or all entries from the registry

**ProgressState inner fields (private):**
```
String stage, message
int percent
boolean done, success
long startedAtMs, updatedAtMs
```

**Private static field:**
```java
private static final ConcurrentHashMap<String, ProgressState> STATES = new ConcurrentHashMap<>()
```

---

### WebConfig.java ⚡
**Type:** Configuration
**Package:** `com.autogradingsystem.web.config`

Implements `WebMvcConfigurer`. Maps `/css/**`, `/js/**`, `/images/**` URL paths to `classpath:/static/css/`, `classpath:/static/js/`, and `classpath:/static/images/` respectively.

---

## 📦 EXTRACTION LAYER

**Package:** `com.autogradingsystem.extraction`
**Purpose:** Phase 2 — Extract and validate student submissions

---

### ExtractionController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.extraction.controller`

**Constructor:**
```java
public ExtractionController(Path csvScoresheet, Path inputSubmissions, Path outputExtracted)
```

```java
public int extractAndValidate() throws IOException
```
- **Workflow:** `cleanOldData()` → `ScoreSheetReader.loadValidStudents()` → `UnzipService.extractAndValidateStudents()` → count results
- **Returns:** Count of all submissions with non-null `resolvedId` (includes `UNRECOGNIZED` — these are still extracted)

> **Note:** Count includes `UNRECOGNIZED` submissions. Pipeline does not abort when unidentified submissions are present.

**Dependencies:** `ScoreSheetReader`, `UnzipService`

---

### UnzipService.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.extraction.service`

**Constructor:**
```java
public UnzipService()
```

```java
public List<ValidationResult> extractAndValidateStudents(
    Path submissionsDir, Path extractedDir, ScoreSheetReader scoreReader
) throws IOException
```
- Finds the newest ZIP in `submissionsDir` → extracts → validates each student ZIP (4-layer) → flattens wrapper folders

```java
private void flattenWrapperFolder(Path studentDir) throws IOException
```
- Calls `findTrueRoot()` recursively — a "true root" is a directory that directly contains at least one `Q{n}` folder
- Moves all contents of the found root to the student's named directory
- Only digs deeper if exactly one non-hidden subdirectory is present (wrapper detection)

**Private fields:** `StudentValidator validator`

**Dependencies:** `StudentValidator`, `ZipFileProcessor`

---

### ScoreSheetReader.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.extraction.service`

Loads official student list from `IS442-ScoreSheet.csv` into a `HashSet` for O(1) lookup.

**CSV Column Mapping:**
```
Col 0: OrgDefinedId   (e.g. #01400001)
Col 1: Username       (e.g. #ping.lee.2023)  ← extracted, # stripped
Col 2: Last Name
Col 3: First Name
Col 4: Email
```

```java
public void loadValidStudents(Path csvPath) throws IOException
public boolean isValid(String username)         // O(1) HashSet lookup
public String resolveUsernameFromIdentifier(String identifier)
```
- `resolveUsernameFromIdentifier` accepts either a plain username or a full email address
- Returns the matched username, or `null` if not found in the scoresheet

```java
public int getStudentCount()
public boolean isEmpty()
```

**Private fields:** `Set<String> validUsernames`, `Map<String, String> emailToUsername`

---

### StudentValidator.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.extraction.service`

**Purpose:** 4-layer student identification.

```java
public ValidationResult validate3Layer(
    Path studentZip, Path destination, ScoreSheetReader scoreReader
) throws IOException
```

**Identification Layers:**

| Layer | Strategy | Details |
|---|---|---|
| 1 | ZIP filename matching | Regex `(?i)(?:\d{4}-\d{4}-)?(.+?)\.zip$` — date prefix `YYYY-YYYY-` is **optional** |
| 2 | Folder name inside ZIP matches scoresheet | Extracts ZIP to temp dir, scans immediate subdirs |
| 3 | `Name:` + `Email ID:` header in `.java` files | Both fields must be present; uses `ScoreSheetReader.resolveUsernameFromIdentifier()` |
| 4 | Extract anyway under raw folder name | Date-prefix stripped; `resolvedId` is non-null but `status` is `UNRECOGNIZED` |

> **Note:** Layer 4 extracts unrecognized submissions rather than discarding them. `resolvedId` is always non-null.

> **Note:** Layer 3 uses `Name:` + `Email ID:` headers, **not** `@author` tags.

**Private constants:**
```java
private static final Pattern ZIP_NAME_PATTERN = Pattern.compile("(?i)(?:\\d{4}-\\d{4}-)?(.+?)\\.zip$")
private static final Pattern NAME_PATTERN      // matches "Name: ..."
private static final Pattern IDENTIFIER_PATTERN // matches "Email ID: ..." / "Email: ..." / "ID: ..."
private static final int HEADER_SCAN_LINES = 12
```

**Dependencies:** `ScoreSheetReader`, `ZipFileProcessor`

---

### HeaderScanner.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.extraction.service`

**Purpose:** Scans main question `.java` files for the student information header. Used for identity resolution when ZIP was not renamed, and for header mismatch detection.

**Expected Header Format:**
```java
/*
 * Name: Ping Lee
 * Email ID: ping.lee.2023@computing.smu.edu.sg
 */
```

**Scan depth:** First **12 lines** of each file only (`HEADER_SCAN_LINES = 12`).

```java
public ScanResult scan(Path studentRoot, List<GradingTask> tasks)
```
- **Scan scope:** Only files matching expected filenames from grading tasks (e.g. `Q1a.java`)
- **File discovery:** `findFileRecursive` — finds files anywhere under `studentRoot`
- Skips files that do not exist (FILE_NOT_FOUND is handled later by grading)

**Inner Class: ScanResult**
```java
public static class ScanResult {
    public String resolvedEmail;         // first email found (null if none)
    public String resolvedName;          // first name found (null if none)
    public String resolvedFromFile;      // which file the email was found in
    public List<String> missingHeaders;  // filenames missing the header
}
```

**Private constants:**
```java
private static final int HEADER_SCAN_LINES = 12
private static final Pattern NAME_PATTERN   // (?im) multiline, matches "Name: ..."
private static final Pattern EMAIL_PATTERN  // (?im) multiline, matches "Email ID|Email|ID: ..."
```

---

### ZipFileProcessor.java 🔧
**Type:** Utility
**Package:** `com.autogradingsystem.extraction.service`

```java
public static void unzip(Path zipFilePath, Path destinationDir) throws IOException
public static boolean isZipFile(Path filePath)
```
- **Security:** Validates every extracted path stays within `destinationDir` (ZIP slip prevention)
- Throws `IOException` with a descriptive message if ZIP slip is detected

---

### ValidationResult.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.extraction.model`

**Fields:**
```java
private final String originalFilename
private final Status status
private final String resolvedId   // username or raw folder name — always non-null
```

**Enum: Status**
```java
MATCHED           // Layer 1: ZIP filename
RECOVERED_FOLDER  // Layer 2: folder name in ZIP
RECOVERED_COMMENT // Layer 3: Name/Email ID header
UNRECOGNIZED      // All layers failed — submission still extracted
```

```java
public boolean isIdentified()   // true if status != UNRECOGNIZED
public boolean isExactMatch()   // true if MATCHED
public boolean wasRecovered()   // true if RECOVERED_FOLDER or RECOVERED_COMMENT
public String getResolvedId()   // username or raw folder name
// plus equals(), hashCode(), toString()
```

---

## 🔍 DISCOVERY LAYER

**Package:** `com.autogradingsystem.discovery`
**Purpose:** Phase 4 — Discover exam structure and build grading plan

---

### DiscoveryController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.discovery.controller`

**Constructor:**
```java
public DiscoveryController(Path inputTemplate, Path inputTesters)
```

```java
public GradingPlan buildGradingPlan() throws IOException
```
- **Workflow:** `findTemplateZip()` (case-insensitive) → `TemplateDiscovery.discoverStructure()` → `TesterDiscovery.discoverTesters()` → `warnOrphanedTesters()` → `GradingPlanBuilder.buildPlan()` → validate not empty → `printDiscoverySummary()`
- Throws `IOException` if plan is empty (no tester–template matches)

---

### TemplateDiscovery.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.discovery.service`

```java
public ExamStructure discoverStructure(Path templateZip) throws IOException
```
- Rejects ZIPs > 50 MB before extraction
- Calls `findRootDirectory()` recursively to handle wrapper folders up to `MAX_NESTING_DEPTH = 20`
- Q folder regex: `^Q[1-9]\d*$` — leading zeros rejected to avoid collision (Q01 and Q1 both parse to 1)
- Collects files recursively from each Q folder; skips Q-named subfolders (Fix-T36)
- Warns if a Q folder has > `MAX_FILES_PER_QUESTION = 50` files

**Private constants** (all `private static final`):
```java
private static final int  MAX_NESTING_DEPTH    = 20
private static final long MAX_ZIP_BYTES        = 50L * 1024 * 1024   // 52,428,800 bytes
private static final int  MAX_FILES_PER_QUESTION = 50
```

---

### TesterDiscovery.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.discovery.service`

```java
public TesterMap discoverTesters(Path testersDir) throws IOException
public boolean isValidTesterFilename(String filename)
```
- Pattern: `*Tester.java` only (not all `*.java`)
- Extracts question ID by stripping `Tester.java` suffix; validates prefix matches `^Q\d+[a-z]?$`
- Normalises to canonical form: `Q` + digits + lowercase letter (e.g. `Q1a`)
- Warns and skips duplicate question IDs

---

### GradingPlanBuilder.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.discovery.service`

```java
public GradingPlan buildPlan(ExamStructure structure, TesterMap testerMap) throws IOException
```
- Matches each question file to its tester via O(1) lowercase key lookup
- Skips `.DS_Store`, `__MACOSX`, and non-Java/class files
- Data files (`.txt`, `.csv`) are logged as `[DATA FILE]` — not graded, used as dependencies
- Deduplicates by task ID — `.java` wins over `.class` for the same question
- Folder-level fallback: if a Q folder has gradable files but none matched a tester, tries matching the folder name itself (handles script/classpath Q4-style tasks)
- Throws `IOException` if zero tasks are produced

```java
public boolean isCompatible(ExamStructure structure, TesterMap testerMap)
```
- Pre-check: returns `true` if at least one tester–template match is possible
- Uses the same `resolveTemplateId()` logic as `buildPlan()` — cannot disagree

---

### ExamStructure.java 📦 / TesterMap.java 📦
**Package:** `com.autogradingsystem.discovery.model`

- `ExamStructure` — immutable `Map<String, List<String>>` of Q folder → sorted file list; defensive copy on construction
- `TesterMap` — immutable `Map<String, String>` of questionId (lowercased) → testerFilename; all keys normalised to lowercase on construction for O(1) case-insensitive lookup

**ExamStructure methods:** `getQuestionFiles()`, `getQuestionFolders()`, `getFilesForQuestion(String)`, `hasQuestion(String)`, `getQuestionCount()`, `getTotalFileCount()`, `isEmpty()`, `isValid()`

**TesterMap methods:** `getTesterMapping()`, `getTesterForQuestion(String)`, `hasTester(String)`, `getQuestionIds()`, `getTesterFilenames()`, `getTesterCount()`, `isEmpty()`, `findOrphanedTesters(Set<String>)`, `findMissingTesters(Set<String>)`

---

## 🤖 TEST CASE GENERATOR LAYER

**Package:** `com.autogradingsystem.testcasegenerator`
**Purpose:** Phase 3 — Generate compilable `*Tester.java` files using LLM

**Design principle:** Testers are generated by the AI from the exam paper PDF (for marks) and the template ZIP (for method signatures and `main()` examples). The examiner reviews and optionally edits them in the Web UI. Once saved via `/save-testers`, they are never overwritten by the pipeline — only by an explicit new generation from the Web UI.

---

### TestCaseGeneratorController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.testcasegenerator.controller`

**Constructor:**
```java
public TestCaseGeneratorController(Path testersDir, Path examDir, Path templateDir)
```

```java
public boolean generateAll(Map<String, QuestionSpec> specs, Map<String, Integer> weights) throws IOException
```
- **Always** clears existing `*Tester.java` files then regenerates
- Called by `TestCaseReviewController /generate-testers` (Web UI "Generate" button)

```java
public boolean generateIfNeeded(Map<String, QuestionSpec> specs, Map<String, Integer> weights) throws IOException
```
- Skips generation entirely if any `*Tester.java` already exists on disk
- Called by `GradingService` during Phase 3 as a fallback
- Also loads exam-derived descriptions and detects script/classpath questions; routes those to `ScriptTesterGenerator`

**Private fields:** `TesterGenerator testerGenerator`, `ScriptTesterGenerator scriptTesterGenerator`, `Path testersDir`, `Path examDir`, `Path templateDir`

---

### TesterGenerator.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Constructors:**
```java
public TesterGenerator()
public TesterGenerator(LLMTestOracle oracle)
```

```java
public String generate(String questionId, QuestionSpec spec, int numTests)
```
- Calls `LLMTestOracle.generateTestCases()` to get `List<GeneratedTestCase>`
- Renders each test case into the IS442 tester format:
  - `public class Q1aTester extends Q1a` when class has no-arg or no constructor
  - `ArrayList<Type> inputs` with `.add()` calls per element for list parameters
  - `runWithTimeout()` wraps every call with a 2-second per-test timeout
  - `score += 1` + `PARTIAL_SCORE:` checkpoint + `Passed`/`Failed` per test
  - `FINAL_SCORE:` emitted in `finally` block of `main()` — always printed even after exceptions
  - Equality strategies: `==` for primitives, `eq()` for floats, `toString().equals()` for custom objects/lists, `DataException` catch block for exception tests

```java
public List<MethodSpec> getTestableMethods(QuestionSpec spec)
```
- Returns public methods excluding `main`, `toString`, `equals`, `hashCode`

**Private field:** `LLMTestOracle oracle`

---

### ScriptTesterGenerator.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Purpose:** Generates fallback testers for script/classpath questions where grading is based on compile/run scripts, folder structure, and classpath/sourcepath setup rather than a Java method call.

```java
public String generate(String questionId, int maxScore, String description)
public String generate(String questionId, int maxScore, String description, Path templateZip)
```
- Builds a compilable `QxTester.java` source dynamically from the exam-derived description
- Detects constraints from description text:
  - `ONE` / `FORBID_BOTH` — one-liner scripts / only one of `.sh`/`.bat` allowed
  - `FORBID_RESOURCE` — scripts must not reference resource folder
  - `EXPECTED_OUT` — required output directory (e.g. `out`)
  - `EXPECTED_MAIN` — expected main class name/package
  - `EXPECTED_STDOUT` — exact expected stdout for run step
  - `STRICT_CP` — classpath must not contain unnecessary entries
  - `KEEP_COMPILE_SCRIPTS` / `KEEP_RUN_SCRIPTS` — compile/run scripts must match template baseline
- Loads baseline script contents from `templateZip` when available for unchanged-script checks
- All checks evaluate to a binary pass/fail; score is `FULL_SCORE` if all `REQUIRED_CHECKS` pass, else 0
- Supports cross-platform grading: tries native `.bat`/`.sh`, falls back to parsed `javac`/`java` command

**Inner classes:** `static class Cmd { List<String> t; String raw; }` and `static class Res { boolean started, ok; String m, o; static pass/fail/skip factories }`

---

### LLMTestOracle.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Constructors:**
```java
public LLMTestOracle(String apiKey)
public static LLMTestOracle fromEnvironment()   // static factory
```

```java
public List<GeneratedTestCase> generateTestCases(
    String questionId, QuestionSpec spec,
    List<MethodSpec> methods, int numTests)
```
- Makes a **single** API call per method
- **Phase 1 — extract main() examples:** parses `{ }` blocks in `main()`, extracts `.add()` calls and `expected = ...` literals verbatim — used as ground-truth anchors
- **Phase 2 — LLM inputs only:** calls LLM for `numTests - len(mainExamples)` additional input sets, asking only for arguments (not expected values)
- **Phase 3 — template oracle:** compiles template source in a temp dir, runs `OracleDriver` to derive expected values by actually executing the reference implementation
- Falls back to smoke tests on any API or compile failure — never blocks the pipeline
- Caches results per `(questionId, methodName, numTests)` key within the same JVM session

**Private fields:** `String apiKey`, `HttpClient http`, `ObjectMapper mapper`, `Map<String, List<GeneratedTestCase>> cache`

---

### LLMTestOracleSupport.java 🔧
**Type:** Utility (package-private)
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Purpose:** Static helper methods shared between `LLMTestOracle` and `TesterGenerator`.

```java
static List<GeneratedTestCase> smokeTestsForInputs(List<List<String>> inputSets)
static List<GeneratedTestCase> padWithSmoke(List<GeneratedTestCase> existing, int targetSize, MethodSpec method)
static String stripPackageDeclaration(String src)
static String buildMethodSignature(MethodSpec method)
static String buildAbstractClassWarning(QuestionSpec spec)
static boolean canExtend(QuestionSpec spec)
static void deleteDirectory(Path dir)
static boolean isListType(String type)
static boolean isPrimitive(String type)
static String extractGenericType(String type)
static boolean isWindows()
static String resolveStrategyForType(String retType)
static List<String> castToStringList(Object obj)
static String oracleSafeDefault(String type, int seed)
```

All methods are package-private (`final class LLMTestOracleSupport`, private constructor).

---

### ExamPaperParser.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Path-Aware Factory:**
```java
public static ExamPaperParser fromEnvironment()              // uses global EXAM_DIR legacy path
public static ExamPaperParser fromEnvironment(Path examDir)  // uses custom, per-assessment examDir
```

**Constructors:**
```java
public ExamPaperParser(String apiKey)
public ExamPaperParser(String apiKey, Path examDir)
```

**Constants:**
```java
public static final Path EXAM_DIR = Paths.get("resources/input/exam");  // legacy single-assessment path
```

```java
public Map<String, Integer> extractMarkWeights()
```
- Reads exam PDF from `resolveExamDir()` via PDFBox (text extraction) or native base64 for Anthropic provider
- Sends extracted text to LLM asking for question→marks JSON
- Returns e.g. `{Q1a→3, Q1b→3, Q2a→5, Q2b→5, Q3→4}`
- Caches result in `cachedWeights` — PDF parsed only once per instance

```java
public Map<String, String> extractQuestionDescriptions()
```
- Extracts concise per-question functional requirements (parsing rules, edge cases, return contracts, exception conditions)
- For script/classpath questions, also preserves script-specific constraints (output directory, main class, resource-folder restrictions, expected stdout)
- Caches result in `cachedDescriptions`; reuses `cachedPdfText` to avoid re-reading the PDF

```java
public Set<String> extractScriptQuestions()
```
- Detects script/classpath questions from exam text based on signal words: `compile.sh`, `compile.bat`, `run.sh`, `run.bat`, `classpath`, `sourcepath`, `folder structure`
- Returns a `Set<String>` of question IDs (e.g. `{"Q4"}`)
- Returns empty set if no exam PDF found or no API key configured

**Private fields:** `String apiKey`, `Path examDir`, `HttpClient http`, `ObjectMapper mapper`, `Map<String,Integer> cachedWeights`, `Map<String,String> cachedDescriptions`, `String cachedPdfText`

---

### LLMConfig.java ⚙️
**Type:** Configuration
**Package:** `com.autogradingsystem.testcasegenerator.service`

Single source of truth for LLM provider. **To switch providers, edit this file only.**

| Constant | Current Value | Purpose |
|---|---|---|
| `PROVIDER` | `"openai"` | Request/response format (Cohere uses OpenAI-compatible endpoint) |
| `API_URL` | `https://api.cohere.com/compatibility/v1/chat/completions` | Cohere compatibility endpoint |
| `MODEL` | `command-r-plus-08-2024` | Free-tier Cohere model |
| `ENV_KEY_NAME` | `COHERE_API_KEY` | Environment variable read at runtime |
| `API_VERSION` | `""` | Unused for Cohere |
| `MAX_TOKENS` | `4096` | Max response tokens |
| `TIMEOUT_S` | `120` | HTTP timeout per request (seconds) |

**API key setup:**
```bash
# .env file in project root (never commit to git)
COHERE_API_KEY=your-key-here
# Free key: https://dashboard.cohere.com/api-keys
```

**Key Methods:**
```java
public static String resolveApiKey()
public static String buildUrl(String apiKey)   // returns API_URL (key unused for Cohere)
public static String buildTextPayload(String prompt, ObjectMapper mapper) throws Exception
public static String buildPdfPayload(String prompt, String base64Pdf, String pdfText, ObjectMapper mapper) throws Exception
public static String extractText(String responseBody, ObjectMapper mapper) throws Exception
public static HttpRequest.Builder addAuthHeaders(HttpRequest.Builder builder, String apiKey)
```

---

### TemplateTestSpecBuilder.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Constructors:**
```java
public TemplateTestSpecBuilder(Path examDir)
public TemplateTestSpecBuilder()    // uses default EXAM_DIR (legacy)
```

```java
public Map<String, QuestionSpec> buildQuestionSpecs(Path templateZip) throws IOException
```
- Extracts template ZIP to temp dir, groups all files by their parent Q folder
- For each `Q*.java` main file: parses it with `QuestionSpecParser`, then attaches all sibling `.java` files as `supportingSourceFiles` and all sibling data files (`.txt`, `.csv`) as `dataFiles`
- Returns `Map<questionId, QuestionSpec>`

```java
public Map<String, Integer> readScoreWeights()
```
- Delegates to `ExamPaperParser.extractMarkWeights()`
- Falls back with a warning message if no weights are returned

**Private fields:** `QuestionSpecParser specParser`, `ExamPaperParser examParser`

---

### QuestionSpecParser.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.testcasegenerator.service`

**Purpose:** Parses a template `.java` file using regex-based line scanning (no external dependencies) to extract the public API surface needed by `LLMTestOracle`.

```java
public QuestionSpec parse(Path templateFile) throws IOException
```
- Detects class name from `public class <Name>` declaration
- Detects public methods (instance and static) with typed parameters
- Detects parameterised constructors → sets `hasParameterisedConstructor`
- Detects instance fields → stored in `fields`
- Stores full source lines in `spec.setSourceLines()` for LLM context
- Falls back to filename-derived class name if no `public class` declaration is found

**Skips:** block comments, line comments, blank lines, Java keywords

---

### QuestionSpec.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.testcasegenerator.model`

| Field | Type | Description |
|---|---|---|
| `className` | `String` | Parsed class name from template |
| `description` | `String` | Per-question functional requirements from exam PDF |
| `methods` | `List<MethodSpec>` | Public non-main methods |
| `fields` | `List<FieldSpec>` | Instance fields |
| `hasParameterisedConstructor` | `boolean` | True if public constructor takes params |
| `sourceLines` | `List<String>` | Full source — sent to LLM for context |
| `supportingSourceFiles` | `Map<String,String>` | Sibling `.java` files (e.g. Shape.java source) |
| `dataFiles` | `Map<String,String>` | Non-Java data files (e.g. persons.txt, students.txt) |

**Inner Models:**
```java
MethodSpec(String name, String returnType, List<ParamSpec> params, boolean isStatic)
    // also: boolean returnsVoid(), boolean returnsPrimitive()
ParamSpec(String type, String name)
FieldSpec(String type, String name)
```

---

### GeneratedTestCase.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.testcasegenerator.model`

| Field | Type | Description |
|---|---|---|
| `args` | `List<String>` | Java literal strings, one per method parameter |
| `expected` | `String` | Java literal for expected return value |
| `equalityStrategy` | `String` | `==`, `eq_float`, `equals`, `list_equals`, `toString_equals`, `exception`, `void_check` |
| `rationale` | `String` | Human-readable explanation (written as comment in tester) |
| `isSmokeTest` | `boolean` | True when only checking no exception is thrown |
| `isVoidCheck` | `boolean` | True for void return type methods |

**Constructors:**
```java
public GeneratedTestCase()
public GeneratedTestCase(List<String> args, String expected, boolean isVoidCheck,
                         boolean isSmokeTest, String rationale, String equalityStrategy)
```

**Factory Methods:**
```java
public static GeneratedTestCase smokeTest(List<String> args, String rationale)
public static GeneratedTestCase voidCheck(List<String> args, String rationale)
```

---

## ⚙️ EXECUTION LAYER

**Package:** `com.autogradingsystem.execution`
**Purpose:** Phase 5 — Execute grading for all students

---

### ExecutionController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.execution.controller`

**Constructor:**
```java
public ExecutionController(Path outputExtracted, Path csvScoresheet,
                           Path inputTesters, Path inputTemplate)
```

**Private fields:**
```java
private final TesterInjector  testerInjector
private final CompilerService compilerService
private final ProcessRunner   processRunner
private final OutputParser    outputParser
private final Map<String, List<String>> remarksAccumulator  // LinkedHashMap, keyed by resolved username
private List<Student> lastGradedStudents                    // all students including anomalies
private final Path outputExtracted, csvScoresheet, inputTesters, inputTemplate
```

**Key Methods:**

```java
public List<GradingResult> gradeAllStudents(GradingPlan plan) throws IOException
public List<GradingResult> gradeAllStudents(GradingPlan plan,
                                            BiConsumer<Integer, Integer> progressCallback) throws IOException
```
- `progressCallback` receives `(completedUnits, totalUnits)` after each task — used by `GradingService` for progress reporting
- **Workflow:**
  1. `loadStudents(plan.getTasks())` — identity resolution + header scanning
  2. Clear `remarksAccumulator`, set `lastGradedStudents`
  3. Pre-populate remarks from identity/header scan flags
  4. For each student × task: `gradeTask()` → accumulate status remarks

```java
public Map<String, String> getRemarksByStudent()
```
- **Returns:** Remarks for **identified students only** — anomaly students excluded
- **Values:** `"All Passed"` if no flags; otherwise `"Q1a:FAILED; NoHeader:Q1b.java"` etc.

```java
public Map<String, String> getAnomalyRemarksByStudent()
```
- **Returns:** Remarks for **anomaly students only**, keyed by raw folder name

```java
public List<Student> getLastGradedStudents()
```
- **Returns:** All graded `Student` objects (identified + anomaly)

**Pre-grading flags — from identity/header scan:**

| Flag | Condition |
|---|---|
| `NoFolderRename` | ZIP was not renamed to student username |
| `NoHeader:Q1a.java` | Q1a.java is missing the student information header |
| `HeaderMismatch: X header found in Q1a.java (ZIP belongs to Y)` | ZIP identity and file header identity conflict — ZIP identity wins |

**Post-grading remarks — per task after grading:**

| Status | Score vs Max | Remark |
|---|---|---|
| `FILE_NOT_FOUND` | any | `Q1a:FILE_NOT_FOUND` |
| `COMPILATION_FAILED` | any | `Q1a:SyntaxError` |
| `TIMEOUT` | score > 0 | `Q1a:PARTIAL TIMEOUT` |
| `TIMEOUT` | score = 0 | `Q1a:TIMEOUT` |
| `COMPLETED` | score = max | *(no remark — perfect)* |
| `COMPLETED` | 0 < score < max | `Q1a:PARTIAL` |
| `COMPLETED` | score = 0 | `Q1a:FAILED` |
| `RUNTIME_ERROR` / `ERROR` / `TESTER_COPY_FAILED` | any | `Q1a:FAILED` |
| `COMPILATION_FAILED` after package stripped | — | `Q1a:WrongPackage:Q1a.java` (in addition to compile remark) |

**Script task routing:**
- If `expectedFile` ends with `.bat`/`.sh` or contains no `.` (folder-style task), `gradeTask()` enters the dynamic script router
- The router searches recursively for `compile.bat`/`compile.sh`/`run.bat`/`run.sh` and falls back to sibling directories for Q4-style relocated scripts

**Private inner class:**
```java
private static class ExtractionMetadata {
    private final String status;
    private final String rawFolderName;
}
```

**Dependencies:** `TesterInjector`, `CompilerService`, `ProcessRunner`, `OutputParser`, `HeaderScanner`, `ScoreAnalyzer`

---

### TesterInjector.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.execution.service`

**Constructor:**
```java
public TesterInjector(Path inputTesters, Path inputTemplate)
```

```java
public void copyTester(String testerFile, Path destinationFolder, String questionFolder) throws IOException
```
- Copies tester from `inputTesters` into the student's question folder before compilation
- Copies non-Java data files from the template question folder (e.g. `persons.txt`, `students.txt`) — skips `compile.bat`, `compile.sh`, `run.bat`, `run.sh` to avoid overwriting student scripts
- **Partial credit injection (the Ninja Move):** After reading the tester source, rewrites every `score += <expr>;` occurrence to also emit a `PARTIAL_SCORE:` checkpoint:
  ```java
  // Original:
  score += 1;
  // After injection:
  score += 1; System.out.println("PARTIAL_SCORE:" + score); System.out.flush();
  ```
- This ensures `OutputParser` can rescue partial marks even if the tester crashes or times out

```java
public void copyTester(String testerFile, Path destinationFolder) throws IOException
```
- 2-param overload — copies tester without copying template data files (used for script tasks)

```java
public boolean testerExists(String testerFile)
```
- Returns `true` if the tester file is present in `inputTesters`

---

### CompilerService.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.execution.service`

**Constant:** `private static final int COMPILER_TIMEOUT_SECONDS = 30`

```java
public boolean compile(Path workingDir)
```
- Compiles all `.java` files in directory — used for script/folder tasks

```java
public boolean compileTargeted(Path workingDir, String targetFile)
```
- Compiles only the target student file + tester files

```java
public CompileResult compileTargetedWithDetails(Path workingDir, String targetFile)
```
- Same as `compileTargeted` but returns a `CompileResult` carrying the list of student files where a package declaration was stripped
- Used by `ExecutionController` to add `WrongPackage` remarks

```java
public CompileResult compileStudentSourcesWithDetails(Path workingDir, String targetFile)
```
- Compiles student source files first (without tester), then the tester separately — used to isolate student compilation errors from tester errors

**Inner Class: CompileResult**
```java
public static class CompileResult {
    public final boolean success;
    public final List<String> strippedPackageFiles;  // student files only, not testers
    public CompileResult(boolean success, List<String> strippedPackageFiles)
}
```

**Package stripping (`stripPackageDeclarations`):**
- Removes `package ...;` line from each file before compilation
- Comments it out: `// [package declaration removed by auto-grader]`
- Only the first occurrence per file is removed

**Classpath:** Includes `workingDir` and any `.jar` files found under `workingDir/external/`

---

### ProcessRunner.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.execution.service`

**Constants:**
```java
private static final int    TIMEOUT_SECONDS = 10
private static final int    MAX_OUTPUT_LINES = 500
private static final String MAX_HEAP = "-Xmx128m"
```

```java
public String runTester(String testerClassName, Path workingDir)
public String runTester(String testerClassName, Path workingDir, String extraArg)
```
- Executes tester class via `java -Xmx128m -cp {classpath} {className} [extraArg]`
- `extraArg` — optional argument passed as `args[0]` to `main()` — used by script testers that need the student's Q folder path
- Merges stderr into stdout (`redirectErrorStream(true)`)
- Captures output in a thread-safe `StringBuffer` via an async reader thread
- **Timeout:** 10 seconds; on timeout, process is killed and partial output is preserved with a `[SYSTEM] TIMEOUT:` trailer
- **Output cap:** 500 lines — excess lines drained but discarded
- Returns full stdout/stderr, or a `TIMEOUT` message, or `ERROR:` prefix on failure

---

### OutputParser.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.execution.service`

```java
public double parseScore(String output)
```

**Parsing strategy (in priority order):**

1. **Pass 0 — PARTIAL_SCORE rescue:** Scans all lines top-to-bottom for `PARTIAL_SCORE:<value>` checkpoints (injected by `TesterInjector`). If found, returns the **highest** value seen — this rescues partial marks before any crash or timeout
2. **Pass 1 — Score label (bottom-up):** Scans lines bottom-up for explicit labels: `Score: 3.0`, `Total: 3.0`, `Points: 3.0`, `Result: 3.0`
3. **Pass 2 — Last number (bottom-up):** Falls back to the last numeric value on the last non-empty, non-`[SYSTEM]` line

Returns `0.0` if nothing is found; floors at `0.0` (negative scores not allowed).

```java
public boolean hasValidScore(String output)
public double[] extractAllNumbers(String output)
```

---

## 📊 ANALYSIS LAYER

**Package:** `com.autogradingsystem.analysis`
**Purpose:** Phase 7 — Analyze results and generate reports

---

### AnalysisController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.analysis.controller`

**Constructor:**
```java
public AnalysisController(Path csvScoresheet, Path outputReports, Path inputTesters,
                          String assessmentTitle)
```
- `assessmentTitle` is the display-formatted name (e.g. `"Lab-Test-1"`) returned by `AssessmentPathConfig.toDisplayTitle()`; used as the file name prefix for exported reports

```java
public void analyzeAndDisplayWithPenalties(
    List<GradingResult> results,
    Map<String, String> remarksByStudent,
    Map<String, String> anomalyRemarks,
    List<Student> allStudents,
    Map<String, String> plagiarismNotes,
    Map<String, ProcessedScore> penaltyResults
)
```
- Single method (no backward-compat overload); pass `Collections.emptyMap()` for `penaltyResults` when penalties are not enabled
- **Workflow:**
  1. `ScoreAnalyzer.inferMaxScores(results, testersDir)`
  2. `ScoreAnalyzer.updateWithMaxScores(results, testersDir)`
  3. Console: `displayQuestionStatistics`, `displayCompactView`, `displayOverallStatistics`
  4. `ScoreSheetExporter.export(...)` — produces the 7-tab combined XLSX with optional penalty columns

> **Note:** Statistics sheets are appended inside `ScoreSheetExporter.export()` by calling `StatisticsReportExporter.appendStatsSheets()` on the shared workbook.

**Dependencies:** `ScoreAnalyzer`, `ScoreSheetExporter`, `ProcessedScore`

---

### ScoreAnalyzer.java 🔧
**Type:** Utility (all static methods, path-aware overloads required)
**Package:** `com.autogradingsystem.analysis.service`

> **Note:** The no-arg overloads (`inferMaxScores(results)`, `getMaxScoreFromTester(questionId)`, `updateWithMaxScores(results)`) throw `UnsupportedOperationException`. Always use the path-aware overloads with a `testersDir` argument.

```java
public static Map<String, Double> inferMaxScores(List<GradingResult> results, Path testersDir)
```
- Always parses the tester file (`score +=` pattern) — never trusts student output
- Supports plain values (`score += 6.0`) and fraction expressions (`score += (3.0/12)`)

```java
public static double getMaxScoreFromTester(String questionId, Path testersDir)
```
- Parses `score +=` occurrences in tester file; sums them up
- Used by `ExecutionController` for PARTIAL/FAILED/WrongPackage thresholds and score capping

```java
public static List<GradingResult> updateWithMaxScores(List<GradingResult> results, Path testersDir)
public static Map<String, List<GradingResult>> groupByStudent(List<GradingResult> results)
public static Map<String, List<GradingResult>> groupByQuestion(List<GradingResult> results)
public static double calculateTotalScore(List<GradingResult> studentResults)
public static double calculateTotalMaxScore(List<GradingResult> studentResults)
public static double calculateAverageScore(List<GradingResult> questionResults)
public static long countPerfect(List<GradingResult> questionResults)
public static long countPassed(List<GradingResult> questionResults)   // score > 0
public static long countFailed(List<GradingResult> questionResults)   // score == 0
```

---

### ScoreSheetExporter.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.analysis.service`

**Constructor:**
```java
public ScoreSheetExporter(Path csvScoresheet, Path outputReports, Path inputTesters,
                          String assessmentTitle)
```
- `assessmentTitle` controls output file names: e.g. `Lab-Test-1-ScoreSheet-Updated.xlsx`

**Purpose:** Produces the single combined `<assessmentTitle>-ScoreSheet-Updated.xlsx` with 7 tabs (+ optional penalty columns), plus a `<assessmentTitle>-ScoreSheet-Updated.csv` sidecar.

**Export overloads:**
```java
public Path export(
    Map<String, List<GradingResult>> resultsByStudent,
    Map<String, String> remarksByStudent,
    Map<String, String> anomalyRemarks,
    List<Student> allStudents) throws IOException
// 4-param: no plagiarism notes, no penalties

public Path export(
    Map<String, List<GradingResult>> resultsByStudent,
    Map<String, String> remarksByStudent,
    Map<String, String> anomalyRemarks,
    List<Student> allStudents,
    Map<String, String> plagiarismNotes) throws IOException
// 5-param: with plagiarism notes, no penalties

public Path export(
    Map<String, List<GradingResult>> resultsByStudent,
    Map<String, String> remarksByStudent,
    Map<String, String> anomalyRemarks,
    List<Student> allStudents,
    Map<String, String> plagiarismNotes,
    Map<String, ProcessedScore> penaltyResults) throws IOException
// 6-param: full, with plagiarism and penalties
```

- When `penaltyResults` is non-empty:
  - **Tab 1 — Score Sheet:** Adds **Penalty Deduction** and **Adjusted Score** columns; LMS numerator column updated to adjusted score
  - Deduction cells with non-zero values are highlighted orange (`FCE4D6`, `makePenaltyStyle()`)
  - **CSV sidecar:** Same two extra columns appended
- **Tab 2 — Anomalies:** Unchanged — submissions not matched to any CSV student
- **Tabs 3–7 — Statistics:** Appended by `StatisticsReportExporter.appendStatsSheets()` on the same open `XSSFWorkbook` before the final `workbook.write()`
- Also produces `<assessmentTitle>-ScoreSheet-Updated.csv`

> **Note:** Statistics are not written to a separate file — they are embedded in the score sheet XLSX.

**CSV column index constants:**
```java
private static final int COL_USERNAME  = 1
private static final int COL_NUMERATOR = 5
private static final int COL_EOL       = 7
```

**Package-private static helpers:**
```java
static String fmtNum(double v)
static int naturalCompare(String a, String b)
```

---

### StatisticsReportExporter.java ⚙️ *(deprecated standalone use)*
**Type:** Service
**Package:** `com.autogradingsystem.analysis.service`

> **@Deprecated:** The standalone `export()` method is a no-op. Statistics are written into the score sheet XLSX via `appendStatsSheets()`.

**Constructor:**
```java
public StatisticsReportExporter(Path outputReports, Path inputTesters)
```

```java
@Deprecated
public Path export(Map<String, List<GradingResult>> resultsByStudent) throws IOException
// → no-op; prints info message and returns null
```

```java
void appendStatsSheets(XSSFWorkbook wb, Map<String, List<GradingResult>> resultsByStudent,
                       Map<String, ProcessedScore> penaltyResults)
```
- **Package-private** — called exclusively by `ScoreSheetExporter.export()`
- Appends 5 sheets to the provided open workbook

**Grade bands:**

| Grade | Range |
|---|---|
| A | ≥ 80% |
| B | 70–79% |
| C | 60–69% |
| D | 50–59% |
| F | < 50% |

**Sheet content:**

| Sheet | Content |
|---|---|
| Dashboard | Class average, median, std deviation, pass/fail rate, highest/lowest score with username |
| Grade Distribution | Count and % per grade band with colour coding |
| Question Analysis | Per-question avg, avg %, highest, lowest, pass/fail count, difficulty rating (Easy/Moderate/Hard/Very Hard) |
| Student Ranking | All students sorted by total score; rank, grade, pass/fail, percentile |
| Performance Matrix | Student × question grid; green=perfect, yellow=partial, red=zero; MAX and CLASS AVERAGE summary rows |

**Package-private sheet builders:** `buildDashboard`, `buildGradeDist`, `buildQuestions`, `buildRanking`, `buildMatrix`, `initStyles`

**Inner class:**
```java
static class StudentRecord {
    String username;
    double total, pct;
    String grade;
    Map<String, Double> qScores;
}
```

**Constants (package-private):**
```java
static final String COL_NAVY      = "1F3864"
static final String COL_BLUE      = "2E75B6"
static final String COL_LIGHT_BLU = "D6E4F0"
static final String COL_GREEN     = "E2EFDA"
static final String COL_RED       = "FCE4D6"
static final String COL_YELLOW    = "FFF2CC"
```

---

## 💰 PENALTY LAYER

**Package:** `com.autogradingsystem.penalty`
**Purpose:** Modular penalty calculations (REST microservice + programmatic API + pipeline integration)

> **Pipeline Integration:** `GradingService.runPenaltyPhase()` invokes `PenaltyController` programmatically when the examiner checks "Apply Penalties" in the UI. The REST endpoints at `/api/penalty/*` remain available for external callers independently.

> **Two penalty paths:** The REST API endpoints (`/api/penalty/*`) use the **strategy-based** path (`PenaltyService.processPenalties()` → `StructuralPenalty`, `CompilationPenalty`). The pipeline integration uses the **rate-based** path (`PenaltyService.processPenaltiesWithGlobalDeductions()`) which applies `rootFolderCorrect`, `properHierarchy`, `hasHeaders`, `hasWrongPackage` flags with fixed percentage rates. The strategy classes are not invoked by the pipeline.

---

### PenaltyController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.penalty.controller`

**Constant:**
```java
public static final String DEFAULT_PENALTIES_CSV = "config/penalties.csv"
```

Registers `StructuralPenalty` and `CompilationPenalty` strategies on construction (used for the REST API path).

**Constructor:**
```java
public PenaltyController()
```

```java
public ProcessedScore processSingleResult(PenaltyGradingResult result)
```
- Applies all registered strategy-based deductions to one result (REST API path)

```java
public ProcessedScore processStudentResults(String studentId, List<PenaltyGradingResult> results)
public ProcessedScore processStudentResults(String studentId, List<PenaltyGradingResult> results,
                                            String penaltiesCsvPath)
```
- Applies rate-based per-question penalties + CSV-based global deductions (pipeline path)

```java
public int getConfiguredStrategyCount()
```
- Returns number of registered `PenaltyStrategy` objects

**Private field:** `PenaltyService penaltyService`

---

### PenaltyService.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.penalty.service`

**Rate constants (private):**
```java
private static final double ROOT_FOLDER_DEDUCTION_RATE = 0.20   // 20% of raw score (folder not renamed)
private static final double HIERARCHY_DEDUCTION_RATE   = 0.05   // 5% of raw score (wrong Q subfolder)
private static final double HEADER_DEDUCTION_RATE      = 0.20   // 20% of raw score (missing header)
private static final double WRONG_PACKAGE_DEDUCTION_RATE = 0.20 // 20% of raw score (wrong package)
```

**Constructor:**
```java
public PenaltyService()
```

```java
public PenaltyService registerStrategy(PenaltyStrategy strategy)  // fluent builder
```

```java
public ProcessedScore processPenalties(PenaltyGradingResult result)
```
- Used for single-result REST API path; invokes registered strategies

```java
public ProcessedScore processPenaltiesWithGlobalDeductions(
    String studentId, List<PenaltyGradingResult> results, String penaltiesCsvPath)
```
- Used for pipeline path; applies rate-based deductions per question then CSV-based global deductions
- `penaltiesCsvPath` may be `null` to skip CSV loading

```java
public int getStrategyCount()
```

---

### PenaltyCalculator.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.penalty.service`

Separate, lower-level calculator used independently of `PenaltyService`.

```java
public void loadExternalPenalties(String filePath)
```
- Reads `penalties.csv`: `studentId, penaltyValue, reason` (positive value = deduction)

```java
public double calculateQuestionScore(String qName, double rawScore,
                                     boolean structErr, boolean headerErr, boolean compileErr)
```
- Structural error → -20%; header error → -20%; compile error → -50%

```java
public double calculateFinalTotal(String studentId, double totalFromAllQuestions)
```
- Applies CSV penalties (added as negative values); floors at 0

```java
public String getFullReport()
```

---

### Penalty Strategies
**Package:** `com.autogradingsystem.penalty.strategies`

- `PenaltyStrategy` — interface with `double calculateDeduction(PenaltyGradingResult result)`
- `StructuralPenalty` — deducts `DEDUCTION_PERCENTAGE = 0.10` (10%) of `maxPossibleScore` if any of `isRootFolderCorrect`, `hasProperHierarchy`, `hasHeaders` is `false`
- `CompilationPenalty` — deducts `DEDUCTION_PERCENTAGE = 0.50` (50%) of `maxPossibleScore` if compilation failed

> Note: These strategy classes use `PenaltyGradingResult`'s legacy 6-param constructor field `hasCompilationError`. The primary pipeline path uses `PenaltyService.processPenaltiesWithGlobalDeductions()` which reads `rootFolderCorrect`, `hasWrongPackage` etc. from the 7-param constructor.

---

### Penalty Models
**Package:** `com.autogradingsystem.penalty.model`

**PenaltyGradingResult:**
```java
// Primary constructor (used by GradingService pipeline):
public PenaltyGradingResult(String questionId, double rawScore, double maxPossibleScore,
                             boolean rootFolderCorrect, boolean properHierarchy,
                             boolean hasHeaders, boolean hasWrongPackage)

// Legacy constructor (used by REST API and strategy path):
public PenaltyGradingResult(double rawScore, double maxPossibleScore,
                             boolean hasCompilationError, boolean namingCorrect,
                             boolean properHierarchy, boolean hasHeaders)
```

Fields (from primary constructor):
```java
private final String  questionId
private final double  rawScore
private final double  maxPossibleScore
private final boolean rootFolderCorrect
private final boolean properHierarchy
private final boolean hasHeaders
private final boolean hasWrongPackage
```

Getters: `getQuestionId()`, `getRawScore()`, `getMaxPossibleScore()`, `isRootFolderCorrect()`, `hasProperHierarchy()`, `hasHeaders()`, `hasWrongPackage()`

**ProcessedScore:**
```java
// Constructors:
public ProcessedScore(double rawScore, double totalDeduction, double finalScore)
public ProcessedScore(double rawScore, double totalDeduction, double finalScore, String penaltyRulesApplied)
public ProcessedScore(double rawScore, double totalDeduction, double finalScore,
                      String penaltyRulesApplied, Map<String, Double> adjustedQuestionScores)
```

Fields:
```java
private final double rawScore
private final double totalDeduction
private final double finalScore              // Math.max(0, rawScore - totalDeduction)
private final String penaltyRulesApplied    // human-readable penalty breakdown
private final Map<String, Double> adjustedQuestionScores  // per-question adjusted scores
```

**PenaltyRecord:** `studentId`, `penaltyValue`, `reason`, `isPercentage`

---

## 🔬 PLAGIARISM LAYER

**Package:** `com.autogradingsystem.plagiarism`
**Purpose:** Phase 6 — Detect suspicious similarity between student submissions

---

### PlagiarismController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.plagiarism.controller`

**Constructors:**
```java
public PlagiarismController(Path outputExtracted, Path outputReports)
public PlagiarismController(Path outputExtracted, Path outputReports, String assessmentTitle)
public PlagiarismController(PlagiarismConfig config, Path outputExtracted, Path outputReports)
public PlagiarismController(PlagiarismConfig config, Path outputExtracted, Path outputReports,
                            String assessmentTitle)
```
- `assessmentTitle` is used to name the plagiarism report: `<assessmentTitle>-Plagiarism-Report.xlsx`
- The pipeline (`GradingService`) uses the 3-param constructor with `assessmentTitle`

```java
public PlagiarismSummary runPlagiarismCheck(GradingPlan gradingPlan)
```
- Skips if `gradingPlan` is null or empty
- Delegates to `PlagiarismDetector.detect()` then `PlagiarismReportExporter.export(allResults, outputReports, assessmentTitle)`
- Returns `PlagiarismSummary`

**`buildPlagiarismNotes` (called from `GradingService`, not on this class) output format:** Per-student readable string combining all flagged pairs:
```
Q1a: flagged with ping.lee.2023 (87.3%); Q2b: flagged with tan.jun.2024 (91.0%)
```

**What plagiarism detection catches vs misses:**

| Scenario | Detected? |
|---|---|
| Copy-paste with renamed variables | ✅ Yes — identifiers normalised to `VAR` |
| Copy-paste with changed string/number values | ✅ Yes — literals normalised |
| Copied single method inside a larger file | ✅ Yes — containment component handles this |
| Identical logic independently written | ⚠️ Possible false positive — review manually |
| Shared boilerplate/starter code | ⚠️ Elevates baseline — consider raising threshold |
| Copying from a previous semester | ❌ No — only compares within current batch |
| Obfuscated logic (loop rewritten as recursion) | ❌ No — structural tokens differ |

**Inner Class: PlagiarismSummary**
```java
public static class PlagiarismSummary {
    public final List<PlagiarismResult> allResults
    public final List<PlagiarismResult> flaggedResults
    public final Map<String, Set<String>> flaggedStudents  // student → set of flagged question IDs
    public final Path reportPath

    public static PlagiarismSummary empty()
    public boolean hasSuspiciousPairs()
    public int getFlaggedPairCount()
    public boolean isStudentFlagged(String studentId)
}
```

**Private fields:** `PlagiarismDetector detector`, `PlagiarismReportExporter exporter`, `PlagiarismConfig config`, `Path outputExtracted`, `Path outputReports`, `String assessmentTitle`

---

### CodeNormalizer.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.plagiarism.service`

```java
public List<String> normalize(String source, int minTokenLength)
public List<String> normalize(String source)   // minTokenLength defaults to 3
```

**Normalisation pipeline:**
1. Strip block comments `/* ... */`
2. Strip line comments `// ...`
3. Replace string literals `"..."` → `STR`
4. Replace char literals `'.'` → `CHR`
5. Replace numeric literals → `NUM`
6. Tokenise on word boundaries + punctuation
7. Replace non-keyword identifiers → `VAR`
8. Drop tokens shorter than `minTokenLength`

Java keywords, operators, and synthetic tokens (`STR`, `CHR`, `NUM`, `VAR`) are preserved verbatim.

---

### FingerprintService.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.plagiarism.service`

```java
public Set<Long> fingerprint(List<String> tokens, int kgramSize, int windowSize)
```
- Implements the **Winnowing algorithm** (Schleimer, Wilkerson & Aiken 2003)
- Builds k-gram hashes using polynomial rolling hash (mod 2³¹−1, base 31)
- Slides a window of `windowSize`; selects minimum hash per window (ties: prefer rightmost)
- Returns `LinkedHashSet<Long>` of selected fingerprints (insertion-ordered)

---

### SimilarityCalculator.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.plagiarism.service`

```java
public double jaccard(Set<Long> fingerprintsA, Set<Long> fingerprintsB)
```
- `|A ∩ B| / |A ∪ B|` — overall structural overlap; returns 0.0 if either set is empty

```java
public double containment(Set<Long> fingerprintsA, Set<Long> fingerprintsB)
```
- `max(|A∩B|/|A|, |A∩B|/|B|)` — detects partial copying from a larger file

```java
public double combined(Set<Long> fingerprintsA, Set<Long> fingerprintsB)
```
- `0.6 × jaccard + 0.4 × containment` — used for flagging decisions

---

### PlagiarismReportExporter.java ⚙️
**Type:** Service
**Package:** `com.autogradingsystem.plagiarism.service`

```java
public Path export(List<PlagiarismResult> allResults, Path outputDir) throws IOException
// → filename: IS442-Plagiarism-Report.xlsx (no title)

public Path export(List<PlagiarismResult> allResults, Path outputDir, String assessmentTitle) throws IOException
// → filename: <assessmentTitle>-Plagiarism-Report.xlsx when assessmentTitle is non-blank
//            falls back to IS442-Plagiarism-Report.xlsx otherwise
```

**Output: `<assessmentTitle>-Plagiarism-Report.xlsx`**

| Sheet | Content |
|---|---|
| Flagged Pairs | Only pairs above threshold, sorted by similarity descending; flagged rows highlighted red |
| All Pairs | Every comparison run, grouped by question; flagged rows highlighted red |

Columns: Question | Student A | Student B | Similarity (%) | Shared FPs | Flagged | Summary

---

### PlagiarismConfig.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.plagiarism.model`

| Field | Default Constant | Description |
|---|---|---|
| `flagThreshold` | `DEFAULT_FLAG_THRESHOLD = 0.80` | Combined similarity score that triggers a flag |
| `minTokenLength` | `DEFAULT_MIN_TOKEN_LENGTH = 3` | Tokens shorter than this are discarded before fingerprinting |
| `windowSize` | `DEFAULT_WINDOW_SIZE = 4` | Winnowing window size |
| `kgramSize` | `DEFAULT_KGRAM_SIZE = 5` | k-gram length |

**Constructors:**
```java
public PlagiarismConfig()                                                           // uses defaults
public PlagiarismConfig(double flagThreshold, int minTokenLength, int windowSize, int kgramSize)
```

---

### PlagiarismResult.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.plagiarism.model`

```java
public PlagiarismResult(String studentA, String studentB, String questionId,
                        double similarityScore, boolean flagged, String summary)
```
- `similarityPercent` is computed from `similarityScore * 100`, rounded to 1 decimal place

Fields: `studentA`, `studentB`, `questionId`, `similarityScore` (in [0,1]), `similarityPercent`, `flagged`, `summary`

```java
public String getPairKey()   // canonical "lo|hi|questionId" — alphabetically ordered
```

---

## 🗂 MULTI-ASSESSMENT LAYER

**Package:** `com.autogradingsystem.multiassessment`
**Purpose:** Provides per-assessment isolated path configuration used by `UnifiedAssessmentController` and `GradingService`.

> **Note:** `AssessmentOrchestrator.java`, `GradingProgressTracker.java`, and `MultiAssessmentController.java` have been removed. The unified wizard grades each assessment sequentially via `POST /assessments/{name}/grade`, which is sufficient for 1–5 assessments and eliminates the complexity of a concurrent thread pool and SSE progress stream.

> **AssessmentBundle.java does not exist** in the current codebase. It was referenced in earlier design documents but was never implemented.

---

### AssessmentPathConfig.java 📦
**Type:** Model / Configuration
**Package:** `com.autogradingsystem.multiassessment`

**Purpose:** Per-assessment instance equivalent of `PathConfig`. Provides all path constants for one assessment's isolated directory. Passed into every path-aware controller constructor.

**Public final path fields:**
```java
public final Path INPUT_BASE        // resources/assessments/<n>/input/
public final Path INPUT_SUBMISSIONS // resources/assessments/<n>/input/submissions/
public final Path INPUT_TEMPLATE    // resources/assessments/<n>/input/template/
public final Path INPUT_TESTERS     // resources/assessments/<n>/input/testers/
public final Path CSV_SCORESHEET    // resources/assessments/<n>/config/scoresheet.csv
public final Path INPUT_EXAM        // resources/assessments/<n>/input/exam/
public final Path OUTPUT_BASE       // resources/assessments/<n>/output/
public final Path OUTPUT_EXTRACTED  // resources/assessments/<n>/output/extracted/
public final Path OUTPUT_REPORTS    // resources/assessments/<n>/output/reports/
```

**Constructor:**
```java
public AssessmentPathConfig(String assessmentName)
```

**Key Methods:**

```java
public static AssessmentPathConfig forName(String rawName)
```
- Sanitises `rawName` for use as a directory name: trim → lowercase → spaces/slashes → hyphens → strip non-alphanumeric → collapse double hyphens
- Example: `"Midterm 2526"` → `"midterm-2526"`

```java
public static String toDisplayTitle(String sanitisedName)
```
- Converts a sanitised assessment name to a display-formatted title for file naming
- Example: `"lab-test-1"` → `"Lab-Test-1"`

```java
public void ensureDirectories()
```
- Creates all required input and output directories using `mkdirs()` — safe to call multiple times

```java
public boolean validateInputPaths()
```
- Returns `true` only if scoresheet CSV, submissions dir, template dir, and testers dir all exist
- Prints `[name] ❌ Missing:` for each absent path

```java
public Path getStudentFolder(String username)
```
- Returns the path to a specific student's folder under `OUTPUT_EXTRACTED`

```java
public Path getStudentQuestionFolder(String username, String questionFolder)
```
- Returns the path to a specific question folder within a student's extracted directory

```java
public String getAssessmentName()  // sanitised directory name
public Path getAssessmentRoot()    // resources/assessments/<sanitised-name>/
```

---

## 🔗 UNIFIED ASSESSMENT LAYER

**Package:** `com.autogradingsystem.web.controller`
**Purpose:** Single entry point for all assessment grading. Handles uploading 1–5 assessments and routes per-assessment wizard steps to path-aware services.

---

### UnifiedAssessmentController.java 🎯
**Type:** Controller
**Package:** `com.autogradingsystem.web.controller`

**Constants:**
```java
private static final int MAX_ASSESSMENTS = 5
```

**Key Routes:**

```java
@PostMapping("/assessments/upload")
public ResponseEntity<Map<String, Object>> handleUpload(
    @RequestParam(value = "name",       required = false) List<String>        names,
    @RequestParam(value = "submission",  required = false) List<MultipartFile> submissions,
    @RequestParam(value = "template",   required = false) List<MultipartFile> templates,
    @RequestParam(value = "scoresheet", required = false) List<MultipartFile> scoresheets,
    @RequestParam(value = "examPdf",    required = false) List<MultipartFile> examPdfs,
    @RequestParam(value = "testers",    required = false) List<MultipartFile> testerZips)
```
- Accepts 1–5 sets of files in a single multipart request; each assessment identified by `name` at the same index
- Creates `AssessmentPathConfig.forName(name)` and `ensureDirectories()`
- Clears any pre-existing assessment directory of the same sanitised name before saving
- If `testerZips[i]` is provided, calls `extractZip()` then `flattenDirectory()` on `INPUT_TESTERS`
- Returns per-assessment `saved`, `missing`, `ready` flags and `sanitisedName`

```java
@GetMapping("/assessments/status")
public ResponseEntity<Map<String, Object>> getStatus()
```
- Scans `resources/assessments/` and returns per-assessment readiness flags
- `hasReports` is `true` when `<assessmentTitle>-ScoreSheet-Updated.xlsx` or `.csv` exists in the reports directory

```java
@PostMapping("/assessments/{name}/parse-exam-marks")
@PostMapping("/assessments/{name}/generate-testers")
@PostMapping("/assessments/{name}/save-testers")
```
- Each instantiates a path-aware `TestCaseReviewController(inputTesters, inputTemplate, inputExam)` and delegates

```java
@PostMapping("/assessments/{name}/grade")
public ResponseEntity<Map<String, Object>> grade(
    @PathVariable String name,
    @RequestParam(value = "applyPenalties", defaultValue = "false") boolean applyPenalties)
```
- Creates `GradingService(AssessmentPathConfig)` and calls `runFullPipeline(name, applyPenalties)`
- `applyPenalties` defaults to `false` — penalty columns only appear when explicitly enabled
- Calls `AssessmentProgressRegistry.start(name)` before grading and `complete()`/`fail()` after
- Returns `{ success, studentCount, logs, assessment, penaltiesApplied, averageScore }`

```java
@GetMapping("/assessments/{name}/progress")
public ResponseEntity<Map<String, Object>> progress(@PathVariable String name)
```
- Returns `AssessmentProgressRegistry.snapshot(name)`

```java
@GetMapping("/assessments/{name}/download")
public ResponseEntity<Resource> download(@PathVariable String name, @RequestParam String file)
```
- `file=scoresheet` → `<assessmentTitle>-ScoreSheet-Updated.xlsx`
- `file=csv` → `<assessmentTitle>-ScoreSheet-Updated.csv`
- `file=plagiarism` → `<assessmentTitle>-Plagiarism-Report.xlsx` (per-assessment path)

```java
@PostMapping("/assessments/clear")
public ResponseEntity<Map<String, Object>> clear(@RequestParam(required=false) String assessment)
```
- Without param: clears all assessments and calls `AssessmentProgressRegistry.clearAll()`
- With param: clears only that assessment directory and its progress entry

**Private helpers:**
- `hasFile(List<MultipartFile>, int)`, `fileAt(List<MultipartFile>, int)` — safe list access
- `missingRequiredUploads(submission, template, scoresheet, examPdf)` — returns list of missing file labels
- `validateAssessmentUpload(assessmentName, placeholder, file)` — validates upload type
- `inspectUpload(MultipartFile)` → `UploadSignature` — detects if upload is PDF, ZIP, CSV, or contains Java/tester files
- `saveFile(MultipartFile, Path)` — saves using absolute path
- `extractZip(MultipartFile, Path)` — extracts with macOS metadata filtering and ZIP-slip prevention
- `flattenDirectory(Path)` — moves nested `.java` files to root, removes empty subdirs
- `hasFilesIn(Path)`, `hasPdfIn(Path)`, `hasJavaFiles(Path)` — existence checks
- `deleteRecursive(Path)` — recursive directory deletion

**Private inner class:**
```java
private static class UploadSignature {
    boolean isPdf, isZip, isCsv, hasJavaFiles, hasTesterJava, hasNestedZip;
}
```

**Dependencies:** `AssessmentPathConfig`, `TestCaseReviewController`, `GradingService`, `AssessmentProgressRegistry`

---

## 📦 SHARED MODELS

**Package:** `com.autogradingsystem.model`
**Purpose:** Data structures shared across all layers

---

### Student.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.model`

**Constructors:**
```java
public Student(String id, Path rootPath)
public Student(String id, String folderPath)
```

**Fields:**
```java
private String id                               // mutable — may be resolved from header email
private final Path rootPath
private boolean folderRenamed = true            // false if ZIP not renamed
private List<String> missingHeaderFiles         // files missing header comment
private boolean anomaly = false                 // true if unidentifiable
private String rawFolderName = null             // original folder name before resolution
private boolean headerMismatch = false          // true if ZIP identity ≠ header identity
private String headerClaimedUsername = null     // username claimed by the mismatched header
private String headerMismatchFile = null        // which file contained the mismatched header
```

**Extra helpers:**
```java
public void setId(String id)
public String getUsername()    // alias for getId()
public Path getQuestionPath(String questionFolder)   // rootPath.resolve(questionFolder)
```

---

### GradingTask.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.model`

**Constructor:**
```java
public GradingTask(String questionId, String testerFile, String studentFolder, String studentFile)
```

**Fields:**
```java
private final String questionId     // e.g. "Q1a"
private final String testerFile     // e.g. "Q1aTester.java"
private final String studentFolder  // e.g. "Q1"
private final String studentFile    // e.g. "Q1a.java"
```

**Helpers:**
```java
public String getTesterClassName()   // strips ".java" → "Q1aTester"
public String getStudentClassName()  // strips ".java" → "Q1a"
// plus equals(), hashCode(), toString()
```

---

### GradingPlan.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.model`

```java
public GradingPlan(List<GradingTask> tasks)  // unmodifiable defensive copy
public List<GradingTask> getTasks()
public int getTaskCount()
public boolean isEmpty()
public GradingTask getTask(int index)
public GradingTask getTaskByQuestionId(String questionId)
public boolean hasTask(String questionId)
public List<String> getQuestionIds()
public Set<String> getQuestionFolders()
public List<GradingTask> getTasksForFolder(String questionFolder)
// plus equals(), hashCode(), toString()
```

---

### GradingResult.java 📦
**Type:** Model
**Package:** `com.autogradingsystem.model`

**Constructors:**
```java
// Full constructor:
public GradingResult(Student student, GradingTask task, double score, double maxScore, String output, String status)
// Without explicit maxScore (maxScore defaults to 0.0):
public GradingResult(Student student, GradingTask task, double score, String output, String status)
// Without explicit status (status derived from score/output):
public GradingResult(Student student, GradingTask task, double score, String output)
```

**Fields:**
```java
private final Student student
private final GradingTask task
private final double score
private final double maxScore
private final String output
private final String status
```

**Status Codes:**

| Status | Meaning |
|---|---|
| `COMPLETED` | Ran successfully (score may be 0, partial, or perfect) |
| `TIMEOUT` | Execution exceeded 10-second time limit |
| `COMPILATION_FAILED` | `javac` returned non-zero exit code |
| `FILE_NOT_FOUND` | Student file or folder not found |
| `RUNTIME_ERROR` | Tester output started with `ERROR:` |
| `TESTER_COPY_FAILED` | Could not copy tester into student folder |
| `ERROR` | Unexpected exception during grading |

**Key Methods:**
```java
public GradingResult withMaxScore(double newMax)  // creates new immutable instance with updated maxScore
public double getScore()
public double getMaxScore()
public String getStatus()
public String getOutput()
public String getQuestionId()       // shortcut for task.getQuestionId()
public boolean isPerfect()          // |score - maxScore| < 0.001 && maxScore > 0
public boolean isPartial()          // score > 0 && !isPerfect()
public boolean isFailed()           // score == 0 && not compile/file error status
public double getPercentage()       // score / maxScore * 100, or 0.0 if maxScore <= 0
// plus toString()
```

---

**END OF COMPONENT REFERENCE**
