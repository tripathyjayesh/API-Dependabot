from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    BaseDocTemplate, Frame, KeepTogether, PageBreak, PageTemplate, Paragraph,
    Spacer, Table, TableStyle,
)


OUT = Path(__file__).resolve().parent
NAVY = colors.HexColor("#16324F")
TEAL = colors.HexColor("#167C80")
PALE = colors.HexColor("#EAF2F5")
INK = colors.HexColor("#243746")
MUTED = colors.HexColor("#607482")

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="DocTitle", parent=styles["Title"], fontName="Helvetica-Bold", fontSize=24, leading=27, textColor=NAVY, alignment=TA_LEFT, spaceAfter=3))
styles.add(ParagraphStyle(name="SubTitle", parent=styles["Normal"], fontName="Helvetica", fontSize=9, leading=12, textColor=MUTED, spaceAfter=10))
styles.add(ParagraphStyle(name="SectionHead", parent=styles["Heading1"], fontName="Helvetica-Bold", fontSize=13, leading=15, textColor=NAVY, spaceBefore=6, spaceAfter=4, keepWithNext=True))
styles.add(ParagraphStyle(name="SubHead", parent=styles["Heading2"], fontName="Helvetica-Bold", fontSize=10, leading=12, textColor=TEAL, spaceBefore=5, spaceAfter=3, keepWithNext=True))
styles.add(ParagraphStyle(name="BodyText2", parent=styles["BodyText"], fontName="Helvetica", fontSize=8.6, leading=11.1, textColor=INK, spaceAfter=4))
styles.add(ParagraphStyle(name="Bullet2", parent=styles["BodyText2"], leftIndent=12, firstLineIndent=-8, spaceAfter=2))
styles.add(ParagraphStyle(name="Cell", parent=styles["BodyText"], fontName="Helvetica", fontSize=7.1, leading=8.7, textColor=INK, spaceAfter=0))
styles.add(ParagraphStyle(name="CellHead", parent=styles["Cell"], fontName="Helvetica-Bold", textColor=colors.white))
styles.add(ParagraphStyle(name="Callout", parent=styles["BodyText2"], fontName="Helvetica-Bold", fontSize=8.8, leading=11.3, textColor=NAVY, spaceAfter=0))
styles.add(ParagraphStyle(name="CodeBlock", parent=styles["BodyText"], fontName="Courier", fontSize=7.2, leading=9.5, textColor=INK, spaceAfter=0))


class SubmissionDoc(BaseDocTemplate):
    def __init__(self, filename, label):
        super().__init__(str(filename), pagesize=A4, leftMargin=17 * mm, rightMargin=17 * mm, topMargin=18 * mm, bottomMargin=16 * mm, title=label, author="API Dependabot project team")
        frame = Frame(self.leftMargin, self.bottomMargin, self.width, self.height, id="main", leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
        self.addPageTemplates([PageTemplate(id="capstone", frames=[frame], onPage=self._header_footer)])

    def _header_footer(self, canvas, doc):
        canvas.saveState()
        canvas.setFillColor(TEAL)
        canvas.setFont("Helvetica-Bold", 7.5)
        canvas.drawString(self.leftMargin, A4[1] - 10 * mm, "API DEPENDABOT  /  AI ENGINEERING CAPSTONE")
        canvas.setStrokeColor(colors.HexColor("#D6E0E5"))
        canvas.setLineWidth(0.5)
        canvas.line(self.leftMargin, A4[1] - 12 * mm, A4[0] - self.rightMargin, A4[1] - 12 * mm)
        canvas.setFillColor(MUTED)
        canvas.setFont("Helvetica", 7.5)
        canvas.drawRightString(A4[0] - self.rightMargin, 8 * mm, f"Capstone submission  |  Page {doc.page}")
        canvas.restoreState()


def p(text, style="BodyText2"):
    return Paragraph(text, styles[style])


def h(text):
    return p(text, "SectionHead")


def sh(text):
    return p(text, "SubHead")


def bullet(text):
    return p("- " + text, "Bullet2")


def table(headers, rows, widths):
    data = [[p(x, "CellHead") for x in headers]]
    data.extend([[p(str(x), "Cell") for x in row] for row in rows])
    t = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CBD7DD")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [PALE, colors.white]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    return t


def callout(text):
    t = Table([[p(text, "Callout")]], colWidths=[176 * mm])
    t.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), PALE), ("BOX", (0, 0), (-1, -1), 0.5, colors.HexColor("#C7D9E0")), ("LEFTPADDING", (0, 0), (-1, -1), 8), ("RIGHTPADDING", (0, 0), (-1, -1), 8), ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6)]))
    return t


def title(text, subtitle):
    return [p(text, "DocTitle"), p(subtitle, "SubTitle")]


design = title("API Dependabot Design Document", "AI Engineering Capstone  |  28 September 2026") + [
    h("Problem statement"),
    p("Java and Spring Boot teams that consume third-party APIs need to determine whether a new API contract affects their client code, where that code lives, and what to review before upgrading. API Dependabot compares two OpenAPI 3.x specifications, searches a Java/Maven consumer repository for supporting evidence, and produces an evidence-cited migration assessment."),
    p("The capstone prototype is limited to OpenAPI 3.x, Java/Spring Boot, Maven, and GitHub repositories. It is an advisory command-line tool. It does not autonomously edit code, run consumer tests in Docker, open a pull request, or deploy a service."),
    h("Architecture"),
    p("A deterministic OpenAPI parser and diff establish contract facts. A repository adapter accepts a local checkout or shallow GitHub clone. Java source and test search returns file and line evidence, while a lexical retriever ranks bounded code, test, build, README, and diff chunks. ReAct tools can inspect the diff, search Java, read bounded Java ranges, and retrieve evidence."),
    table(["Stage", "Implementation"], [
        ["Contract analysis", "Parse old and new OpenAPI documents; report changes and review notes."],
        ["Repository surface", "Search Java source/tests in a local checkout or shallow GitHub snapshot."],
        ["Retrieval", "Rank 25-line evidence chunks with 5-line overlap and retain source locations."],
        ["Vanilla RAG", "Retrieve once, then make a single evidence-grounded model request."],
        ["ReAct", "Use a read-only tool loop for diff inspection, Java search/read, and retrieval; maximum eight calls."],
    ], [34 * mm, 142 * mm]),
    h("Comparison"),
    p("Promptfoo runs both approaches on the same six labeled development cases with the same deterministic symbol assertion and LLM-as-a-judge rubric. The latest rerun passed 6/6 for each approach. The first run exposed an unsupported ReAct claim; a grounding instruction was revised and the case passed on rerun. These cases are not held out and do not settle the architecture choice."),
    PageBreak(),
    h("Evaluation criteria"),
    table(["Measure", "Purpose"], [
        ["Pass rate and judge score", "Compare whether answers address the expected migration assessment and remain grounded."],
        ["Evidence support", "Check whether repository-specific claims cite actual tool evidence and paths."],
        ["Latency", "Measure answer time for practical use."],
        ["Tool calls", "Quantify ReAct orchestration effort."],
        ["Human review", "Review correctness, clarity, uncertainty, and unsupported claims; expand for held-out runs."],
    ], [41 * mm, 135 * mm]),
    h("Framework justification"),
    p("Java 21, Spring Boot, and Maven match the target consumer ecosystem and developer background. Spring AI supplies model and tool-calling integration while keeping deterministic diff and retrieval components independently inspectable. OpenAPI parser/diff tooling avoids hand-written specification parsing, with curated fixtures guarding library behavior. Promptfoo provides paired evaluation. A vector database, PostgreSQL, and multi-agent orchestration were not added because the current scope does not justify their complexity."),
    h("Boundaries and next validation"),
    bullet("Lexical Java search may miss generated, reflective, indirect, or non-Java call paths."),
    bullet("A model, service, or test mentioning a field does not prove it sends an affected API request."),
    bullet("The LLM judge is not ground truth; six development cases and one rerun are not a held-out study."),
    bullet("GitHub support reads a shallow snapshot; no PR creation, isolated consumer test execution, hosted HTTP API, or deployment is implemented."),
    Spacer(1, 4),
    callout("Next evaluation: add held-out cases, compare on identical inputs, and manually review a sample before selecting an architecture."),
]

report = title("API Dependabot Project Documentation", "AI Engineering Capstone  |  Final implementation and evaluation report  |  28 September 2026") + [
    h("Executive summary"),
    p("API Dependabot is a capstone prototype for identifying potential Java consumer impact when an OpenAPI 3.x provider contract changes. It combines deterministic contract comparison, repository search and lexical retrieval, and two answer strategies: Vanilla RAG and a ReAct tool-using agent."),
    p("The latest six-case evaluation passed all 12 approach-case executions. Vanilla RAG passed 6/6, and ReAct passed 6/6 after a grounding instruction change. This is development-set evidence, not a final architecture decision: the set is small, there is no held-out assessment, and the LLM judge needs human review."),
    h("Problem and scope"),
    p("A changed API specification does not by itself tell a consumer team whether its Java code uses the affected operation or property. This project compares old and new OpenAPI files and connects changes to evidence in a Maven/Spring Boot consumer repository."),
    bullet("Supported: OpenAPI 3.x, Java/Spring Boot, Maven, local repositories, and GitHub repository snapshots."),
    bullet("Output: deterministic contract changes, relevant Java evidence, and an evidence-cited migration assessment."),
    bullet("Not implemented: hosted HTTP service, automatic code edits, Docker-isolated consumer tests, GitHub pull request creation, auto-merge, or deployment."),
    callout("This is a review assistant prototype, not a production Dependabot service."),
    PageBreak(),
    h("System design and implementation"),
    sh("Contract and repository analysis"),
    p("The Java application parses and compares two OpenAPI documents through a deterministic diff layer. Known classification gaps are surfaced as review notes rather than treated as proof of safety. The repository service uses either a local folder or shallow GitHub clone. Private repository credentials are read from GITHUB_TOKEN and are not embedded in the repository URL."),
    sh("Retrieval and answers"),
    p("Java source, tests, README, Maven configuration, and the contract diff are split into bounded chunks. A deterministic BM25-style lexical ranker returns relevant chunks with file and line ranges. No vector database is used. Vanilla RAG retrieves once and makes one model request. ReAct can inspect the diff, search Java, read a bounded Java range, and retrieve evidence through read-only, repository-scoped tools; it has an eight-call limit."),
    sh("Grounding behavior"),
    p("ReAct instructions require evidence linking a changed operation and field to a request/response call site or mapping before claiming repository-specific impact. If the connection is missing, the answer should state the gap and frame actions conditionally. Both approaches cite evidence identifiers; recommendations remain advisory."),
    h("Framework choices"),
    table(["Choice", "Reason"], [
        ["Java 21 / Spring Boot / Maven", "Matches the user and consumer ecosystem; keeps the CLI and services in one stack."],
        ["Spring AI", "Provides model integration and tool calling while diff/retrieval remain separate."],
        ["OpenAPI parser/diff", "Handles specification syntax; fixtures make classification behavior testable."],
        ["Promptfoo", "Runs both approaches on shared labeled inputs with deterministic and judge assertions."],
    ], [52 * mm, 124 * mm]),
    PageBreak(),
    h("Evaluation method"),
    p("The Promptfoo suite contains six development cases, each executed once with Vanilla RAG and once with ReAct. Cases cover a removed response property, removed operation, added operation, newly required request field, renamed path parameter, and Stripe Basil-to-Clover migration. Each answer is checked for a case-specific symbol and graded with the same LLM-as-a-judge rubric for correctness, evidence, and uncertainty."),
    p("The application defaults to gpt-5-mini unless APIDEPENDABOT_MODEL overrides it. The saved summary does not record an effective override, and the rubric grader model is not explicitly pinned in the evaluation config; record both explicitly for future comparable runs."),
    p("The validation script also runs Maven tests and an optional public GitHub checkout/search smoke check before evaluation. The latest script run reported 26 automated tests passing and the GitHub smoke check passing."),
    h("Comparative results"),
    table(["Run", "Vanilla RAG", "ReAct", "Interpretation"], [
        ["Initial six-case run", "6/6; judge 1.00; median 14.8 s", "5/6; judge 0.92; median 29.5 s", "ReAct said sample service/tests needed changes without evidence of an API call."],
        ["Rerun after grounding change", "6/6; judge 1.00; median 12.4 s", "6/6; judge 1.00; median 33.5 s; 42 tool calls", "The prior failing case passed; one rerun does not prove general improvement."],
    ], [31 * mm, 37 * mm, 43 * mm, 65 * mm]),
    p("In the rerun, both approaches passed all 12 provider-case results. ReAct's median latency was about 2.7 times Vanilla RAG's, and ReAct made 42 tool calls across six answers. Quality, latency, and orchestration cost need assessment on held-out cases and human review before an architecture choice."),
    PageBreak(),
    h("Failure analysis and decisions"),
    sh("Unsupported repository impact"),
    p("On the initial run, ReAct correctly identified that POST /widgets required a new category property. It then claimed the sample Widget model, service, and tests needed changes even though repository evidence did not show that those files sent the affected request. The judge rejected the unsupported impact claim. The system prompt was revised to require a call-site or payload-mapping link before asserting repository impact. The answer passed on the same six-case set. This is a promising fix for the observed case, not proof that all such errors are resolved."),
    sh("Earlier implementation pivots"),
    p("The README records implementation failures and pivots: adapting to the diff library's writer/resource behavior on Windows; separately surfacing removed response fields and renamed parameters when library classifications did not; distinguishing identifier references from declarations in lexical search; improving ranking after irrelevant chunks outranked code; and fixing invalid bounded-file-read calls before evaluation."),
    h("Known limitations"),
    bullet("Only six curated development cases have been run, with one rerun after a prompt revision; there is no held-out set."),
    bullet("The judge can miss errors or reward plausible wording. Manual review is required."),
    bullet("Lexical matching can miss indirect/generated/reflection use and does not trace AST call graphs."),
    bullet("No code patching, Docker-based test execution, PR creation, HTTP API, auth layer, or deployed service is included."),
    bullet("The GitHub smoke test verifies shallow checkout and search against Spring Petclinic, not all repository shapes."),
    PageBreak(),
    h("Reproduction steps"),
    p("Install Java 21, Git, and Node.js 22.22.0 or later with npm. From the cloned project root, set model access and run the validation script in PowerShell:"),
]
code_table = Table([[p('$env:OPENAI_API_KEY = "your-api-key"<br/>$env:SPRING_AI_MODEL_CHAT = "openai"<br/>.\\scripts\\run-capstone-validation.ps1 -GitHubRepository "https://github.com/spring-projects/spring-petclinic"', "CodeBlock")]], colWidths=[176 * mm])
code_table.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#F3F6F8")), ("BOX", (0, 0), (-1, -1), 0.35, colors.HexColor("#D6E0E5")), ("LEFTPADDING", (0, 0), (-1, -1), 7), ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6)]))
report.extend([code_table, Spacer(1, 4), p("The script builds the JAR, runs Maven verification with the portable Eclipse compiler profile, checks out and searches the optional GitHub repository, evaluates all six shared cases, and writes evaluation/promptfoo/summary.json. Detailed outputs are in evaluation/promptfoo/results.json. A failed assertion returns a nonzero status after the summary is written. Model answers make live provider calls."), h("Three-minute demo walkthrough"), table(["Time", "Show and explain"], [
    ["0:00-0:30", "State the scope: OpenAPI 3.x changes, Java/Spring impact, advisory CLI."],
    ["0:30-1:00", "Run the deterministic diff; point out the removed response property."],
    ["1:00-1:35", "Show Vanilla RAG on the sample question and its source citations."],
    ["1:35-2:10", "Show ReAct's read-only tool trace and repository evidence."],
    ["2:10-2:45", "Open the summary; compare paired results and describe the failure/prompt pivot."],
    ["2:45-3:00", "State limitations and the next step: held-out cases and human review."],
], [25 * mm, 151 * mm]), callout("Recording tip: use saved evaluation outputs. Rehearse with only the two live demo calls."),])


onepager = title("API Dependabot", "AI Engineering Capstone  |  One-page project summary  |  28 September 2026") + [
    h("Problem"),
    p("When an API provider changes its OpenAPI contract, Java/Spring Boot teams must work out whether the change affects their Maven consumer and what to review before upgrading. API Dependabot connects deterministic contract comparison to evidence from the consuming repository."),
    h("What the prototype does"),
    table(["Input", "Analysis", "Output"], [[
        "Old and new OpenAPI 3.x files plus a local or GitHub Java repository.",
        "Diff the contract, search Java source/tests, rank lexical evidence, and answer through paired Vanilla RAG or ReAct paths.",
        "Contract changes, file/line evidence, and an advisory migration assessment with citations.",
    ]], [47 * mm, 73 * mm, 56 * mm]),
    h("Architecture and active data surface"),
    p("The deterministic diff is the source of contract facts. The repository surface supports local checkouts and shallow GitHub snapshots. A BM25-style lexical retriever indexes Java, tests, build metadata, README content, and diff evidence in bounded chunks. Vanilla RAG retrieves once; ReAct can inspect the diff, search/read Java, and retrieve more evidence with read-only tools and an eight-call limit. Spring AI provides model/tool integration; Promptfoo runs the paired evaluation."),
    h("Evaluation snapshot"),
    table(["Approach", "Latest six-case rerun", "Median latency", "Tool calls"], [
        ["Vanilla RAG", "6/6 passed; mean judge 1.00", "12.4 s", "0"],
        ["ReAct", "6/6 passed; mean judge 1.00", "33.5 s", "42 total"],
    ], [31 * mm, 69 * mm, 34 * mm, 42 * mm]),
    p("An earlier run exposed one unsupported ReAct impact claim about a request field. After adding a requirement for call-site or payload-mapping evidence, that case passed on rerun. Six development cases and one rerun are not a held-out study; these results do not select a final architecture."),
    h("Scope and next step"),
    p("The prototype is an advisory CLI, not a hosted Dependabot service. It does not edit code, run consumer tests in Docker, open pull requests, or deploy. Lexical matching may miss indirect or generated call paths. Next: add held-out cases, review answers manually, and compare quality with latency and tool use before deciding between approaches."),
    Spacer(1, 3),
    callout("Reproduce from the project root: set OPENAI_API_KEY and SPRING_AI_MODEL_CHAT=openai, then run scripts/run-capstone-validation.ps1. The evaluation makes live model calls."),
]


SubmissionDoc(OUT / "API_Dependabot_One_Pager.pdf", "API Dependabot One Pager").build(onepager)
SubmissionDoc(OUT / "API_Dependabot_Design_Document.pdf", "API Dependabot Design Document").build(design)
SubmissionDoc(OUT / "API_Dependabot_Project_Documentation.pdf", "API Dependabot Project Documentation").build(report)
