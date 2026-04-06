# IS442 Auto-Grading System

A Spring Boot web application that automatically grades Java programming assignments. Upload student submissions, a template, and a scoresheet CSV, and the system compiles code, runs tester programs, detects plagiarism, and exports scored reports through a browser-based UI.

Built by **G1T4**. GitHub Link: https://github.com/jiaqianwan2024/IS442-Auto-Grading-System 

---

## Features

- **Multi-assessment support** — grade up to 5 assessments simultaneously in separate tabs
- **AI-assisted test generation** — uses Cohere LLM to generate Java tester source files from an exam PDF and template ZIP (requires `COHERE_API_KEY`)
- **Examiner-confirmed marking** — review and edit AI-generated testers before grading; confirmed marks are persisted to `marks.json`
- **Automatic grading** — extracts student ZIPs, compiles Java files, runs testers, and collects scores per question
- **Penalty engine** — structural and compilation penalties are supported in the grading pipeline and applied by the web UI grading flow
- **Plagiarism detection** — similarity analysis across all submissions; exports a separate XLSX plagiarism report
- **Score sheet export** — downloads a structured Excel report with per-question raw/final scores, denominator, grading remarks, penalty notes, and plagiarism flags
- **Identity resolution** — matches submission folder names to LMS usernames via email headers and scoresheet CSV; flags anomalies
- **Submission tolerance** — handles wrong folder names, missing question folders, nested file structures, wrong packages, and pre-compiled `.class`-only submissions

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 21 |
| Maven | 3.9+ (bundled at `maven/apache-maven-3.9.9/`) |
| Cohere API key | Free tier at [dashboard.cohere.com](https://dashboard.cohere.com/api-keys) |

---

## Quick Start

### 1. Set your API key

Create a `.env` file in the project root:

```
COHERE_API_KEY=your-key-here
```

### 2. Run the server

**macOS / Linux:**
```bash
./run-web.sh
```

**Windows:**
```bat
run-web.bat
```

The script will:
1. Clear any leftover assessment data from previous runs
2. Load the API key from `.env`
3. Build the project with `mvn clean install`
4. Start the server at **http://localhost:9090**

> **Note:** `run-web.sh` requires executing permissions. If needed: `chmod +x run-web.sh`

---

## Usage

### Step 1 — Upload

Open `http://localhost:9090` in your browser. Add 1–5 assessment slots and upload for each:

| File | Required | Description |
|---|---|---|
| Student Submissions | Yes | ZIP file of all student submission ZIPs (LMS bulk download) |
| Template | Yes | ZIP file with the model solution folder structure (Q1/, Q2/, …) |
| Score Sheet CSV | Yes | LMS-exported grade centre CSV with student usernames and emails |
| Exam PDF | Yes in current web upload flow | Exam paper used by the AI to generate test cases and extract question marks |
| Tester ZIP | Optional | Pre-prepared tester `.java` files; skips AI generation if uploaded |

Click **Upload All** to ingest the files.

### Step 2 — Confirm Marks

Review the per-question marks extracted from the exam PDF. Edit any values and click **Confirm Marks** to save them. These become the ground-truth denominators used in the final score sheet.

### Step 3 — Review Testers

The AI generates a Java tester file for each question. Review and edit the generated code in the browser editor, then click **Save All** to write the testers to disk.

If you already have tester files (from a previous run or prepared manually), upload them directly and skip AI generation.

### Step 4 — Grade

Click **Grade**. The system will:

1. Extract and identify each student submission
2. Resolve student identities from folder names and file headers
3. Compile each student's Java files
4. Run the saved testers against each submission
5. Apply penalties for folder naming, hierarchy, missing headers, and wrong package declarations
6. Detect cross-submission plagiarism
7. Export results to XLSX

### Step 5 — Download Results

When grading completes, download:
- **Score Sheet-Updated** — per-student, per-question scores (raw and final), remarks, penalties, plagiarism flag
- **Plagiarism Report (Excel)** — flagged similar pairs with similarity percentages

---

## File Structure (at runtime)

Assessment data is stored under `resources/assessments/<name>/` relative to the project root:

```
resources/assessments/<name>/
├── config/
│   └── scoresheet.csv          # LMS grade centre CSV
├── input/
│   ├── submissions/            # Student submission ZIP(s)
│   ├── template/               # Template ZIP
│   ├── testers/                # Generated or uploaded tester .java files
│   │   └── marks.json          # Examiner-confirmed marks (denominator source of truth)
│   └── exam/                   # Exam paper PDF
└── output/
    ├── extracted/              # Unzipped and identity-resolved student submissions
    └── reports/                # Score sheet XLSX, plagiarism report XLSX, and CSV sidecar
```

> This directory is **cleared on every server startup** by `run-web.sh` / `run-web.bat`.

---

## Template & Tester Conventions

### Template ZIP structure

```
template.zip
└── (optional wrapper folder)
    ├── Q1/
    │   ├── Q1a.java
    │   └── Q1b.java
    ├── Q2/
    │   └── Q2a.java
    └── Q3/
        └── Q3.java
```

- Top-level question folders must match the pattern `Q<n>` (e.g., `Q1`, `Q2`, `Q10`)
- Sub-question files use the pattern `Q<n><letter>.java` (e.g., `Q1a.java`, `Q1b.java`)
- Helper files (data files, utilities) can be placed in the same folders

### Tester file naming

Tester filenames must follow the convention `<QuestionId>Tester.java`:

| Question file | Tester file |
|---|---|
| `Q1a.java` | `Q1aTester.java` |
| `Q2b.java` | `Q2bTester.java` |
| `Q3.java` | `Q3Tester.java` |

---

## Student Submission Conventions

Student ZIPs should contain question folders mirroring the template structure. The system handles common deviations:

- **Wrong folder names** — folder is flagged as `WRONG_FOLDER_NAME`; files inside are not graded
- **Missing question folder** — file found anywhere else in the submission is still graded with an `ImproperHierarchy` remark
- **Wrong package declaration** — `WrongPackage` remark added; fallback compilation attempted
- **Pre-compiled `.class` only** — `FILE_NOT_FOUND` (source `.java` is required)
- **Script-based questions** (Q4-style) — if `compile.sh` / `run.sh` are detected, the script task path is used

---

## Score Sheet Columns

| Column | Description |
|---|---|
| LMS columns 0–5 | Directly copied from the LMS CSV (username, name, student ID, etc.) |
| Calculated Raw Grade Numerator | Sum of raw per-question scores |
| Calculated Raw Grade Denominator | Total marks, preferably from `marks.json`, otherwise inferred from tester source |
| Q1a (raw), Q1b (raw), … | Per-question raw score |
| Grading Remarks | Compilation errors, file-not-found, wrong package, etc. |
| Calculated Final Grade Numerator | After penalty deductions |
| Calculated Final Grade Denominator | Same as raw denominator |
| Q1a (final), Q1b (final), … | Per-question final score after penalties |
| Penalty Remarks | Penalty breakdown per question |
| Plagiarism | Flagged if similarity detected |

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.2 |
| Frontend | Thymeleaf, vanilla JS (single-page) |
| Excel export | Apache POI 5.2.3 |
| PDF parsing | Apache PDFBox 3.0.1 |
| JSON | Jackson Databind |
| AI (tester generation) | Cohere API via OpenAI-compatible HTTP calls |
| Build | Maven 3.9 |

---

## Project Structure

```
src/main/java/com/autogradingsystem/
├── Main.java                        # Spring Boot entry point
├── analysis/                        # Score aggregation and sheet export
├── discovery/                       # Template + tester discovery, grading plan builder
├── execution/                       # Core grading engine (compile, run, identity resolution)
├── extraction/                      # ZIP extraction and student identity matching
├── model/                           # GradingTask, GradingResult, GradingPlan, Student
├── multiassessment/                 # Per-assessment path isolation (AssessmentPathConfig)
├── penalty/                         # Penalty strategies (compilation, structural)
├── plagiarism/                       # Similarity detection and report export
├── testcasegenerator/               # AI-based tester generation from exam PDF
└── web/                             # Spring MVC controllers and GradingService pipeline
```

---

## Configuration

| Property | Default | Description |
|---|---|---|
| `server.port` | `9090` | HTTP port |
| `spring.servlet.multipart.max-file-size` | `100MB` | Max single file upload size |
| `spring.servlet.multipart.max-request-size` | `100MB` | Max total upload request size |

Set in `src/main/resources/application.properties`.
