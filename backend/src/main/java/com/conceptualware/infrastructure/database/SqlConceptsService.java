package com.conceptualware.infrastructure.database;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;

/**
 * Concept #11 — Databases: SQL Concepts (via H2 in-memory database):
 *
 *   DDL (Data Definition Language):
 *     CREATE TABLE, ALTER TABLE, DROP TABLE, CREATE INDEX, CREATE VIEW.
 *
 *   DML (Data Manipulation Language):
 *     INSERT, UPDATE, DELETE, SELECT, MERGE (UPSERT).
 *
 *   DCL (Data Control Language):
 *     GRANT, REVOKE (permission management).
 *
 *   TCL (Transaction Control Language):
 *     BEGIN TRANSACTION, COMMIT, ROLLBACK, SAVEPOINT.
 *
 *   Normalization: 1NF → 2NF → 3NF → BCNF
 *     1NF: Atomic values, no repeating groups.
 *     2NF: 1NF + no partial dependencies (all non-key cols depend on whole PK).
 *     3NF: 2NF + no transitive dependencies (non-key cols only depend on PK).
 *     BCNF: Stricter 3NF — every determinant is a candidate key.
 *
 *   JOINs: INNER, LEFT OUTER, RIGHT OUTER, FULL OUTER, CROSS, SELF.
 *
 *   Advanced SQL:
 *     CTEs (Common Table Expressions): WITH recursive_cte AS (...)
 *     Window Functions: ROW_NUMBER(), RANK(), DENSE_RANK(), LAG(), LEAD(), PARTITION BY
 *     Subqueries: correlated, scalar, EXISTS
 *     Stored Procedures (H2 functions simulate these)
 *
 *   MVCC (Multi-Version Concurrency Control):
 *     Each transaction sees a snapshot of the database at its start time.
 *     Isolation levels: READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE.
 *
 * Concept #11 — OLTP vs OLAP, ETL, Data Warehouse
 */
@Service
@Transactional
public class SqlConceptsService {

    private final JdbcTemplate jdbc;

    public SqlConceptsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        initializeSchema();
    }

    // ── DDL: Schema Creation ──────────────────────────────────────────────────

    private void initializeSchema() {
        // Normalized schema demonstrating 1NF → BCNF

        // employees: 1NF (atomic values, primary key)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS departments (
                id   INT PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                budget DECIMAL(15,2)
            )
            """);

        // 2NF: employee data fully depends on employee_id (not partial key)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS employees (
                id            INT PRIMARY KEY,
                name          VARCHAR(100) NOT NULL,
                email         VARCHAR(150) UNIQUE NOT NULL,
                salary        DECIMAL(10,2),
                department_id INT REFERENCES departments(id),
                hire_date     DATE,
                manager_id    INT REFERENCES employees(id)
            )
            """);

        // 3NF: project depends on project_id; no transitive dependency
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS projects (
                id         INT PRIMARY KEY,
                name       VARCHAR(100),
                budget     DECIMAL(15,2),
                start_date DATE,
                status     VARCHAR(20) CHECK (status IN ('active', 'completed', 'cancelled'))
            )
            """);

        // Junction table (BCNF: no non-key attributes)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS employee_projects (
                employee_id INT REFERENCES employees(id),
                project_id  INT REFERENCES projects(id),
                role        VARCHAR(50),
                hours_week  INT,
                PRIMARY KEY (employee_id, project_id)
            )
            """);

        // View: CREATE VIEW (virtual table)
        jdbc.execute("""
            CREATE OR REPLACE VIEW employee_summary AS
            SELECT
                e.id,
                e.name,
                e.salary,
                d.name AS department,
                COUNT(ep.project_id) AS project_count
            FROM employees e
            LEFT JOIN departments d ON e.department_id = d.id
            LEFT JOIN employee_projects ep ON e.id = ep.employee_id
            GROUP BY e.id, e.name, e.salary, d.name
            """);

        // Index for query optimization
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_employees_dept ON employees(department_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_employees_email ON employees(email)");

        seedData();
    }

    private void seedData() {
        // INSERT (DML) — idempotent check via MERGE
        jdbc.execute("MERGE INTO departments (id, name, budget) VALUES (1, 'Engineering', 1000000)");
        jdbc.execute("MERGE INTO departments (id, name, budget) VALUES (2, 'Marketing', 500000)");
        jdbc.execute("MERGE INTO departments (id, name, budget) VALUES (3, 'HR', 300000)");

        jdbc.execute("MERGE INTO employees VALUES (1, 'Alice', 'alice@cw.com', 95000, 1, '2020-01-15', NULL)");
        jdbc.execute("MERGE INTO employees VALUES (2, 'Bob',   'bob@cw.com',   80000, 1, '2021-03-01', 1)");
        jdbc.execute("MERGE INTO employees VALUES (3, 'Carol', 'carol@cw.com', 90000, 1, '2019-07-20', 1)");
        jdbc.execute("MERGE INTO employees VALUES (4, 'Dave',  'dave@cw.com',  70000, 2, '2022-05-10', NULL)");
        jdbc.execute("MERGE INTO employees VALUES (5, 'Eve',   'eve@cw.com',   75000, 2, '2021-11-01', 4)");

        jdbc.execute("MERGE INTO projects VALUES (1, 'ConceptualWare', 500000, '2024-01-01', 'active')");
        jdbc.execute("MERGE INTO projects VALUES (2, 'DataPipeline',   200000, '2024-03-01', 'active')");
        jdbc.execute("MERGE INTO projects VALUES (3, 'Legacy Migration', 150000, '2023-01-01', 'completed')");

        jdbc.execute("MERGE INTO employee_projects VALUES (1, 1, 'lead', 30)");
        jdbc.execute("MERGE INTO employee_projects VALUES (2, 1, 'developer', 40)");
        jdbc.execute("MERGE INTO employee_projects VALUES (3, 2, 'architect', 20)");
        jdbc.execute("MERGE INTO employee_projects VALUES (1, 2, 'manager', 10)");
        jdbc.execute("MERGE INTO employee_projects VALUES (4, 3, 'analyst', 40)");
    }

    // ── DML: SELECT with all JOIN types ──────────────────────────────────────

    /** INNER JOIN: only matching rows in both tables. */
    public List<Map<String, Object>> innerJoin() {
        return jdbc.queryForList("""
            SELECT e.name AS employee, d.name AS department
            FROM employees e
            INNER JOIN departments d ON e.department_id = d.id
            ORDER BY e.name
            """);
    }

    /** LEFT OUTER JOIN: all employees, even without a department. */
    public List<Map<String, Object>> leftJoin() {
        return jdbc.queryForList("""
            SELECT e.name, d.name AS dept
            FROM employees e
            LEFT JOIN departments d ON e.department_id = d.id
            """);
    }

    /** SELF JOIN: find each employee's manager. */
    public List<Map<String, Object>> selfJoin() {
        return jdbc.queryForList("""
            SELECT e.name AS employee, m.name AS manager
            FROM employees e
            LEFT JOIN employees m ON e.manager_id = m.id
            ORDER BY e.name
            """);
    }

    /** CROSS JOIN: Cartesian product — all combinations. */
    public List<Map<String, Object>> crossJoin() {
        return jdbc.queryForList("""
            SELECT d.name AS dept, p.name AS project
            FROM departments d
            CROSS JOIN projects p
            WHERE p.status = 'active'
            ORDER BY d.name, p.name
            """);
    }

    // ── Window Functions ──────────────────────────────────────────────────────

    /**
     * Window Functions: aggregate WITHOUT collapsing rows.
     * ROW_NUMBER, RANK, DENSE_RANK, LAG, LEAD, NTILE, SUM OVER.
     */
    public List<Map<String, Object>> windowFunctions() {
        return jdbc.queryForList("""
            SELECT
                e.name,
                e.salary,
                d.name AS department,
                ROW_NUMBER()  OVER (ORDER BY e.salary DESC)                  AS overall_rank,
                RANK()        OVER (PARTITION BY d.id ORDER BY e.salary DESC) AS dept_rank,
                DENSE_RANK()  OVER (PARTITION BY d.id ORDER BY e.salary DESC) AS dept_dense_rank,
                e.salary - LAG(e.salary, 1, e.salary)  OVER (PARTITION BY d.id ORDER BY e.salary DESC) AS salary_diff_from_above,
                LEAD(e.name, 1, 'N/A') OVER (PARTITION BY d.id ORDER BY e.salary DESC)                  AS next_by_salary,
                SUM(e.salary)   OVER (PARTITION BY d.id)                      AS dept_total_salary,
                AVG(e.salary)   OVER (PARTITION BY d.id)                      AS dept_avg_salary,
                NTILE(2)        OVER (ORDER BY e.salary)                      AS salary_quartile
            FROM employees e
            JOIN departments d ON e.department_id = d.id
            ORDER BY d.name, dept_rank
            """);
    }

    // ── CTEs (Common Table Expressions) ──────────────────────────────────────

    /** Recursive CTE: compute organizational hierarchy. */
    public List<Map<String, Object>> recursiveCTE() {
        return jdbc.queryForList("""
            WITH RECURSIVE org_hierarchy(id, name, manager_id, level, path) AS (
                -- Base case: top-level employees (no manager)
                SELECT id, name, manager_id, 0, CAST(name AS VARCHAR(500))
                FROM employees WHERE manager_id IS NULL

                UNION ALL

                -- Recursive case: employees who report to someone
                SELECT e.id, e.name, e.manager_id, h.level + 1,
                       CONCAT(h.path, ' > ', e.name)
                FROM employees e
                JOIN org_hierarchy h ON e.manager_id = h.id
            )
            SELECT id, name, level, path FROM org_hierarchy ORDER BY path
            """);
    }

    /** Non-recursive CTE: summarize per department (improves readability vs subquery). */
    public List<Map<String, Object>> nonRecursiveCTE() {
        return jdbc.queryForList("""
            WITH dept_stats AS (
                SELECT
                    department_id,
                    COUNT(*) AS headcount,
                    AVG(salary) AS avg_salary,
                    MAX(salary) AS max_salary
                FROM employees
                GROUP BY department_id
            ),
            expensive_depts AS (
                SELECT department_id FROM dept_stats WHERE avg_salary > 75000
            )
            SELECT d.name, ds.headcount, ds.avg_salary
            FROM departments d
            JOIN dept_stats ds ON d.id = ds.department_id
            WHERE d.id IN (SELECT department_id FROM expensive_depts)
            ORDER BY ds.avg_salary DESC
            """);
    }

    // ── Subqueries ────────────────────────────────────────────────────────────

    /** Correlated subquery: for each employee, find how many teammates they have. */
    public List<Map<String, Object>> correlatedSubquery() {
        return jdbc.queryForList("""
            SELECT e.name,
                (SELECT COUNT(*) FROM employees e2
                 WHERE e2.department_id = e.department_id AND e2.id <> e.id) AS teammates
            FROM employees e
            ORDER BY e.name
            """);
    }

    /** EXISTS subquery: employees assigned to at least one project. */
    public List<Map<String, Object>> existsSubquery() {
        return jdbc.queryForList("""
            SELECT e.name FROM employees e
            WHERE EXISTS (
                SELECT 1 FROM employee_projects ep WHERE ep.employee_id = e.id
            )
            ORDER BY e.name
            """);
    }

    // ── Transaction Isolation Levels (MVCC demonstration) ────────────────────

    /**
     * READ COMMITTED: default in PostgreSQL/Oracle.
     * Each statement sees only committed data as of the statement's start.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Map<String, Object>> readCommittedQuery() {
        return jdbc.queryForList("SELECT id, name, salary FROM employees ORDER BY id");
    }

    /**
     * REPEATABLE READ: default in MySQL InnoDB.
     * All reads in a transaction see data as of transaction start — prevents non-repeatable reads.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<Map<String, Object>> repeatableReadQuery() {
        // First read — establishes snapshot
        List<Map<String, Object>> first = jdbc.queryForList("SELECT id, salary FROM employees");
        // Second read in same transaction — guaranteed to return same data (REPEATABLE READ)
        return jdbc.queryForList("SELECT id, salary FROM employees");
    }

    /**
     * SERIALIZABLE: strongest isolation.
     * Transactions execute as if serial — prevents phantom reads.
     * Performance: highest overhead due to range locks.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<Map<String, Object>> serializableQuery() {
        return jdbc.queryForList("SELECT COUNT(*) AS count, AVG(salary) AS avg FROM employees");
    }

    // ── Normalization demonstration ───────────────────────────────────────────

    /**
     * Demonstrates normalization violations and their resolution.
     * The denormalized table below violates multiple normal forms.
     */
    public void createNormalizationDemo() {
        // Violates 1NF (multiple values in skills column), 2NF (dept_location depends on dept, not employee)
        // 3NF (dept_manager depends on dept_id, not emp_id)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS unnormalized_example (
                emp_id      INT,
                emp_name    VARCHAR(100),
                skills      VARCHAR(200), -- '1NF VIOLATION: java,python,sql'
                dept_id     INT,
                dept_name   VARCHAR(100), -- '2NF VIOLATION: depends on dept_id only'
                dept_manager VARCHAR(100) -- '3NF VIOLATION: transitive dependency'
            )
            """);

        // This is then resolved into: employees, departments, employee_skills (our existing schema)
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS employee_skills (
                employee_id INT REFERENCES employees(id),
                skill       VARCHAR(50),
                PRIMARY KEY (employee_id, skill)
            )
            """);

        jdbc.execute("MERGE INTO employee_skills VALUES (1, 'Java')");
        jdbc.execute("MERGE INTO employee_skills VALUES (1, 'MongoDB')");
        jdbc.execute("MERGE INTO employee_skills VALUES (2, 'TypeScript')");
        jdbc.execute("MERGE INTO employee_skills VALUES (2, 'React')");
    }

    // ── OLTP vs OLAP examples ─────────────────────────────────────────────────

    /** OLTP query: fast point lookup (milliseconds). */
    @Transactional(readOnly = true)
    public Map<String, Object> oltpQuery(int employeeId) {
        return jdbc.queryForMap("SELECT * FROM employees WHERE id = ?", employeeId);
    }

    /** OLAP query: full table scan aggregate (seconds on large data). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> olapQuery() {
        return jdbc.queryForList("""
            SELECT
                d.name AS department,
                COUNT(e.id)        AS employee_count,
                SUM(e.salary)      AS total_payroll,
                AVG(e.salary)      AS avg_salary,
                MIN(e.salary)      AS min_salary,
                MAX(e.salary)      AS max_salary,
                COUNT(DISTINCT ep.project_id) AS projects
            FROM departments d
            LEFT JOIN employees e        ON d.id = e.department_id
            LEFT JOIN employee_projects ep ON e.id = ep.employee_id
            GROUP BY d.id, d.name
            ORDER BY total_payroll DESC
            """);
    }

    // ── ETL Pipeline simulation ────────────────────────────────────────────────

    /**
     * ETL: Extract → Transform → Load.
     * Extract: read from source (employees table).
     * Transform: calculate derived metrics.
     * Load: write to data warehouse table.
     */
    public int runETLPipeline() {
        // Create data warehouse fact table
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS dw_employee_facts (
                snapshot_date  DATE,
                department     VARCHAR(100),
                headcount      INT,
                total_salary   DECIMAL(15,2),
                avg_salary     DECIMAL(10,2),
                project_count  INT
            )
            """);

        // Extract + Transform + Load in one SQL (INSERT ... SELECT)
        return jdbc.update("""
            INSERT INTO dw_employee_facts
            SELECT
                CURRENT_DATE,
                d.name,
                COUNT(DISTINCT e.id),
                SUM(e.salary),
                AVG(e.salary),
                COUNT(DISTINCT ep.project_id)
            FROM departments d
            LEFT JOIN employees e ON d.id = e.department_id
            LEFT JOIN employee_projects ep ON e.id = ep.employee_id
            GROUP BY d.name
            """);
    }

    // ── Stored procedure equivalent (H2 function) ────────────────────────────

    public void createStoredProcedures() {
        // H2 uses ALIAS for stored procedures — same concept as MySQL/PostgreSQL procedures
        jdbc.execute("""
            CREATE ALIAS IF NOT EXISTS GET_DEPT_SALARY AS '
            import java.sql.*;
            @CODE
            ResultSet getDeptSalary(Connection conn, int deptId) throws SQLException {
                PreparedStatement ps = conn.prepareStatement(
                    "SELECT SUM(salary) as total FROM employees WHERE department_id = ?");
                ps.setInt(1, deptId);
                return ps.executeQuery();
            }
            '
            """);
    }

    public Object callGetDeptSalary(int deptId) {
        return jdbc.queryForObject("CALL GET_DEPT_SALARY(?)", Object.class, deptId);
    }
}
