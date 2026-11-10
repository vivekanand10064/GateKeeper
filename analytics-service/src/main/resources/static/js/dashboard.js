
const API_BASE_URL = "http://localhost:8082/api/analytics";
const MANAGEMENT_URL = "http://localhost:8081/";
const MANAGEMENT_ORIGIN = "http://localhost:8081";

let authToken = null;
let performanceChart = null;
let statusChart = null;
let trafficTrendChart = null;
let routeTrafficChart = null;

let currentEvents = [];

/* Theme */

function initializeTheme() {
    const savedTheme = localStorage.getItem("gatekeeper-theme");
    applyTheme(savedTheme === "dark" ? "dark" : "light");
}

function applyTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);

    const isDark = theme === "dark";

    document.getElementById("themeToggleIcon").textContent =
        isDark ? "☀️" : "🌙";

    document.getElementById("themeToggleText").textContent =
        isDark ? "Light mode" : "Dark mode";

    document.getElementById("themeToggleButton").setAttribute(
        "aria-label",
        isDark ? "Switch to light mode" : "Switch to dark mode"
    );

    localStorage.setItem("gatekeeper-theme", theme);

    if (performanceChart || statusChart) {
        updateChartTheme();
    }
}

function toggleTheme() {
    const current =
        document.documentElement.getAttribute("data-theme") || "light";

    applyTheme(current === "light" ? "dark" : "light");
}

function getThemeColors() {
    const styles = getComputedStyle(document.documentElement);

    return {
        text: styles.getPropertyValue("--muted").trim(),
        grid: styles.getPropertyValue("--border").trim(),
        min: styles.getPropertyValue("--chart-min").trim(),
        average: styles.getPropertyValue("--chart-avg").trim(),
        max: styles.getPropertyValue("--chart-max").trim(),
        success: styles.getPropertyValue("--chart-success").trim(),
        client: styles.getPropertyValue("--chart-client").trim(),
        server: styles.getPropertyValue("--chart-server").trim()
    };
}

function updateChartTheme() {
    const colors = getThemeColors();

    if (performanceChart) {
        performanceChart.data.datasets[0].backgroundColor = [
            colors.min,
            colors.average,
            colors.max
        ];

        performanceChart.options.plugins.legend.labels.color = colors.text;
        performanceChart.options.scales.x.ticks.color = colors.text;
        performanceChart.options.scales.x.grid.color = colors.grid;
        performanceChart.options.scales.y.ticks.color = colors.text;
        performanceChart.options.scales.y.grid.color = colors.grid;

        performanceChart.update();
    }

    if (statusChart) {
        statusChart.data.datasets[0].backgroundColor = [
            colors.success,
            colors.client,
            colors.server
        ];

        statusChart.options.plugins.legend.labels.color = colors.text;
        statusChart.update();
    }
	if (trafficTrendChart) {
	    trafficTrendChart.data.datasets[0].borderColor = colors.average;
	    trafficTrendChart.data.datasets[0].backgroundColor =
	        getComputedStyle(document.documentElement)
	            .getPropertyValue("--accent-soft").trim();

	    trafficTrendChart.options.plugins.legend.labels.color = colors.text;
	    trafficTrendChart.options.scales.x.ticks.color = colors.text;
	    trafficTrendChart.options.scales.x.grid.color = colors.grid;
	    trafficTrendChart.options.scales.y.ticks.color = colors.text;
	    trafficTrendChart.options.scales.y.grid.color = colors.grid;

	    trafficTrendChart.update();
	}

	if (routeTrafficChart) {
	    routeTrafficChart.data.datasets[0].backgroundColor = [
	        colors.average,
	        colors.success,
	        colors.client,
	        colors.server,
	        colors.max
	    ];

	    routeTrafficChart.options.plugins.legend.labels.color = colors.text;
	    routeTrafficChart.update();
	}
}

/* Sidebar */

function initializeSidebar() {
    const toggleButton =
        document.getElementById("sidebarToggleButton");

    toggleButton.addEventListener("click", () => {
        const collapsed =
            document.body.classList.toggle("sidebar-collapsed");

        updateSidebarButton(collapsed);
        resizeCharts();
    });

    document.addEventListener("mousemove", event => {
        if (
            document.body.classList.contains("sidebar-collapsed") &&
            event.clientX <= 10
        ) {
            document.body.classList.remove("sidebar-collapsed");
            updateSidebarButton(false);
            resizeCharts();
        }
    });

    document.addEventListener("keydown", event => {
        if (
            event.key === "Escape" &&
            !document.body.classList.contains("sidebar-collapsed")
        ) {
            document.body.classList.add("sidebar-collapsed");
            updateSidebarButton(true);
            resizeCharts();
        }
    });

    document.querySelectorAll(".sidebar .nav-item").forEach(item => {
        item.addEventListener("click", event => {
            const targetId = item.getAttribute("href");

            if (!targetId || !targetId.startsWith("#")) return;

            const target = document.querySelector(targetId);

            if (!target) return;

            event.preventDefault();

            document.querySelectorAll(".sidebar .nav-item").forEach(link => {
                link.classList.toggle("active", link === item);
            });

            target.scrollIntoView({
                behavior: "smooth",
                block: "start"
            });

            document.body.classList.add("sidebar-collapsed");
            updateSidebarButton(true);

            window.history.replaceState(null, "", targetId);

            setTimeout(resizeCharts, 280);
        });
    });
}

function updateSidebarButton(collapsed) {
    const button = document.getElementById("sidebarToggleButton");

    document.getElementById("sidebarToggleIcon").textContent = "☰";

    button.setAttribute(
        "aria-label",
        collapsed ? "Expand sidebar" : "Collapse sidebar"
    );

    button.setAttribute(
        "title",
        collapsed ? "Show navigation" : "Hide navigation"
    );
}

function resizeCharts() {
    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            if (performanceChart) performanceChart.resize();
            if (statusChart) statusChart.resize();
            if (trafficTrendChart) trafficTrendChart.resize();
            if (routeTrafficChart) routeTrafficChart.resize();
        });
    });
}

/* Authentication */

function redirectToLogin() {
    window.location.replace(MANAGEMENT_URL);
}

function requestAuthentication() {
    if (!window.opener) {
        redirectToLogin();
        return;
    }

    window.addEventListener("message", event => {
        if (
            event.origin !== MANAGEMENT_ORIGIN ||
            event.source !== window.opener ||
            event.data?.type !== "GATEKEEPER_ANALYTICS_AUTH_RESPONSE"
        ) {
            return;
        }

        const session = event.data;

        if (
            !session.token ||
            session.role !== "TENANT_ADMIN" ||
            !session.organizationId
        ) {
            redirectToLogin();
            return;
        }

        authToken = session.token;
        loadDashboard();
    });

    window.opener.postMessage(
        { type: "GATEKEEPER_ANALYTICS_AUTH_REQUEST" },
        MANAGEMENT_ORIGIN
    );

    setTimeout(() => {
        if (!authToken) redirectToLogin();
    }, 8000);
}

/* API requests */

async function fetchAnalytics(endpoint, startDate, endDate) {
    if (!authToken) {
        redirectToLogin();
        throw new Error("Authentication required.");
    }

    const params = new URLSearchParams({
        startDate,
        endDate
    });

    const response = await fetch(
        `${API_BASE_URL}/${endpoint}?${params.toString()}`,
        {
            headers: {
                Authorization: `Bearer ${authToken}`
            }
        }
    );

    if (response.status === 401 || response.status === 403) {
        redirectToLogin();
        throw new Error("Your session is invalid or expired.");
    }

    if (!response.ok) {
        throw new Error(`Analytics API error: ${response.status}`);
    }

    return response.json();
}

/* Date range filter */

function formatLocalDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");

    return `${year}-${month}-${day}`;
}

function getDateRange() {
    const range = document.getElementById("dateRangeSelect").value;

    const today = new Date();
    const endDate = formatLocalDate(today);

    if (range === "custom") {
        const start = document.getElementById("startDateInput").value;
        const end = document.getElementById("endDateInput").value;

        if (!start || !end) {
            throw new Error("Please select both start and end dates.");
        }

        if (start > end) {
            throw new Error("Start date cannot be after end date.");
        }

        if (end > endDate) {
            throw new Error("End date cannot be in the future.");
        }

        return {
            startDate: `${start}T00:00:00`,
            endDate: `${end}T23:59:59`
        };
    }

    const start = new Date(today);
    start.setHours(0, 0, 0, 0);

    if (range === "7days") {
        start.setDate(start.getDate() - 6);
    } else if (range === "30days") {
        start.setDate(start.getDate() - 29);
    }

    return {
        startDate: `${formatLocalDate(start)}T00:00:00`,
        endDate: `${endDate}T23:59:59`
    };
}

function initializeDateFilter() {
    const rangeSelect = document.getElementById("dateRangeSelect");
    const customFields = document.getElementById("customDateFields");
    const applyButton = document.getElementById("applyDateFilterButton");

    const today = formatLocalDate(new Date());

    document.getElementById("endDateInput").max = today;
    document.getElementById("startDateInput").max = today;

    rangeSelect.addEventListener("change", () => {
        const isCustom = rangeSelect.value === "custom";

        customFields.hidden = !isCustom;
        applyButton.textContent = isCustom ? "Apply Filter" : "Apply Filter";

        if (isCustom) {
            document.getElementById("endDateInput").value = today;

            const start = new Date();
            start.setDate(start.getDate() - 6);

            document.getElementById("startDateInput").value =
                formatLocalDate(start);
        }
    });

    applyButton.addEventListener("click", async () => {
        await loadDashboard();
    });

    document.getElementById("startDateInput").addEventListener("change", event => {
        document.getElementById("endDateInput").min = event.target.value;
    });
}

/* Summary cards */

function updateSummary(data) {
    document.getElementById("totalRequests").textContent =
        data.totalRequests ?? 0;

    document.getElementById("successfulRequests").textContent =
        data.successfulRequests ?? 0;

    document.getElementById("failedRequests").textContent =
        data.failedRequests ?? 0;
}

function updatePerformance(data) {
    document.getElementById("averageResponseTime").textContent =
        `${Number(data.averageResponseTimeMs ?? 0).toFixed(2)} ms`;
}

/* Route table */

function renderRouteTable(routes) {
    const table = document.getElementById("routeTable");

    if (!routes.length) {
        table.innerHTML =
            '<tr><td colspan="5" class="empty">No route data available</td></tr>';
        return;
    }

    table.innerHTML = routes.map(route => `
        <tr>
            <td>${escapeHtml(route.routeId)}</td>
            <td>${route.totalRequests ?? 0}</td>
            <td class="success">${route.successfulRequests ?? 0}</td>
            <td class="failure">${route.failedRequests ?? 0}</td>
            <td>${Number(route.averageResponseTimeMs ?? 0).toFixed(2)}</td>
        </tr>
    `).join("");
}

/* API Health Monitoring */

function renderHealthTable(routes) {
    const table = document.getElementById("healthTable");
	// Update API health summary counters
	const healthyCount = routes.filter(
	    route => route.healthStatus === "HEALTHY"
	).length;

	const warningCount = routes.filter(
	    route => route.healthStatus === "WARNING"
	).length;

	const criticalCount = routes.filter(
	    route => route.healthStatus === "CRITICAL"
	).length;

	document.getElementById("healthyRouteCount").textContent = healthyCount;
	document.getElementById("warningRouteCount").textContent = warningCount;
	document.getElementById("criticalRouteCount").textContent = criticalCount;

    if (!routes.length) {
        table.innerHTML =
            '<tr><td colspan="5" class="empty">No API health data available</td></tr>';
        return;
    }

    table.innerHTML = routes.map(route => {
        const status = route.healthStatus ?? "UNKNOWN";

        const badgeClass = {
            HEALTHY: "health-healthy",
            WARNING: "health-warning",
            CRITICAL: "health-critical"
        }[status] || "";

        return `
            <tr>
                <td>${escapeHtml(route.routeId)}</td>

                <td>
                    <span class="health-badge ${badgeClass}">
                        ${escapeHtml(status)}
                    </span>
                </td>

                <td>
                    ${Number(route.errorRatePercent ?? 0).toFixed(2)}%
                </td>

                <td>
                    ${Number(route.averageResponseTimeMs ?? 0).toFixed(2)}
                </td>

                <td class="health-details">
                    ${escapeHtml(route.healthMessage ?? "No health details available")}
                </td>
            </tr>
        `;
    }).join("");
}

/* Render API Health Alerts */

function renderHealthAlerts(routes) {
    const alertsList = document.getElementById("healthAlertsList");
    const alertCount = document.getElementById("healthAlertCount");

    const alerts = [];

    routes.forEach(route => {
        const errorRate = Number(route.errorRatePercent ?? 0);
        const responseTime = Number(route.averageResponseTimeMs ?? 0);

        if (errorRate >= 20) {
            alerts.push({
                routeId: route.routeId,
                severity: "critical",
                title: "Critical error rate",
                message: `Error rate is ${errorRate.toFixed(2)}%`
            });
        } else if (errorRate >= 5) {
            alerts.push({
                routeId: route.routeId,
                severity: "warning",
                title: "Elevated error rate",
                message: `Error rate is ${errorRate.toFixed(2)}%`
            });
        }

        if (responseTime > 2000) {
            alerts.push({
                routeId: route.routeId,
                severity: "warning",
                title: "High response time",
                message: `Average response time is ${responseTime.toFixed(2)} ms`
            });
        }
    });

    alertCount.textContent =
        `${alerts.length} ${alerts.length === 1 ? "Alert" : "Alerts"}`;

    if (!alerts.length) {
        alertsList.innerHTML = `
            <div class="health-alert-empty">
                No health alerts detected. All monitored routes are within thresholds.
            </div>
        `;
        return;
    }

    alertsList.innerHTML = alerts.map(alert => `
        <div class="health-alert-item ${alert.severity}">
            <div class="health-alert-content">
                <strong>${escapeHtml(alert.title)}</strong>
                <span>Route: ${escapeHtml(alert.routeId)}</span>
                <p>${escapeHtml(alert.message)}</p>
            </div>
            <span class="health-alert-badge ${alert.severity}">
                ${alert.severity.toUpperCase()}
            </span>
        </div>
    `).join("");
}


/* Recent API requests */

function renderRequestTable(events) {
    const table = document.getElementById("requestTable");

    if (!events.length) {
        table.innerHTML =
            '<tr><td colspan="5" class="empty">No requests found for this date range</td></tr>';
        return;
    }

    const sortedEvents = [...events].sort(
        (a, b) => new Date(b.timestamp) - new Date(a.timestamp)
    );

    table.innerHTML = sortedEvents.map(event => {
        const statusClass =
            event.responseStatus >= 500
                ? "failure"
                : event.responseStatus >= 400
                    ? "warning"
                    : "success";

        return `
            <tr>
                <td>${escapeHtml(event.requestMethod)}</td>
                <td>${escapeHtml(event.requestPath)}</td>
                <td class="${statusClass}">
                    ${event.responseStatus ?? "--"}
                </td>
                <td>${Number(event.responseTimeMs ?? 0).toFixed(2)} ms</td>
                <td>${escapeHtml(
                    event.timestamp
                        ? new Date(event.timestamp).toLocaleString()
                        : "--"
                )}</td>
            </tr>
        `;
    }).join("");
}

/* Request status chart */

function renderStatusChart(summary) {
    const context = document.getElementById("statusChart");

    if (statusChart) statusChart.destroy();

    const colors = getThemeColors();

    statusChart = new Chart(context, {
        type: "doughnut",

        data: {
            labels: [
                "Successful",
                "Client Errors",
                "Server Errors"
            ],

            datasets: [{
                data: [
                    summary.successfulRequests ?? 0,
                    summary.clientErrors ?? 0,
                    summary.serverErrors ?? 0
                ],

                backgroundColor: [
                    colors.success,
                    colors.client,
                    colors.server
                ],

                borderColor: getComputedStyle(
                    document.documentElement
                ).getPropertyValue("--surface").trim(),

                borderWidth: 3,
                hoverOffset: 5
            }]
        },

        options: {
            responsive: true,
            maintainAspectRatio: false,

            plugins: {
                legend: {
                    position: "bottom",
                    labels: {
                        color: colors.text,
                        padding: 18,
                        usePointStyle: true
                    }
                }
            }
        }
    });
}

/* Performance chart */

function renderPerformanceChart(metrics) {
    const context = document.getElementById("performanceChart");

    if (performanceChart) performanceChart.destroy();

    const colors = getThemeColors();

    performanceChart = new Chart(context, {
        type: "bar",

        data: {
            labels: [
                "Minimum",
                "Average",
                "Maximum"
            ],

            datasets: [{
                label: "Response Time (ms)",

                data: [
                    metrics.minimumResponseTimeMs ?? 0,
                    metrics.averageResponseTimeMs ?? 0,
                    metrics.maximumResponseTimeMs ?? 0
                ],

                backgroundColor: [
                    colors.min,
                    colors.average,
                    colors.max
                ],

                borderRadius: 5,
                maxBarThickness: 90
            }]
        },

        options: {
            responsive: true,
            maintainAspectRatio: false,

            plugins: {
                legend: {
                    labels: {
                        color: colors.text,
                        usePointStyle: true
                    }
                }
            },

            scales: {
                x: {
                    ticks: { color: colors.text },
                    grid: { color: colors.grid }
                },

                y: {
                    beginAtZero: true,
                    ticks: { color: colors.text },
                    grid: { color: colors.grid }
                }
            }
        }
    });
}

/* =========================================
   API TRAFFIC ANALYTICS
========================================= */

function updateTrafficSummary(data) {

    document.getElementById("trafficTotalRequests").textContent =
        data.totalRequests ?? 0;

    document.getElementById("requestsPerMinute").textContent =
        data.requestsPerMinute ?? 0;

    document.getElementById("requestsPerHour").textContent =
        data.requestsPerHour ?? 0;

    document.getElementById("peakRequestCount").textContent =
        data.peakRequestCount ?? 0;

    document.getElementById("peakTrafficTime").textContent =
        data.peakTrafficTime && data.peakTrafficTime !== "No traffic"
            ? data.peakTrafficTime.replace("T", " ")
            : "No traffic recorded";
}


function renderTrafficTrendChart(points) {

    const context = document.getElementById("trafficTrendChart");

    if (trafficTrendChart) {
        trafficTrendChart.destroy();
    }

    const colors = getThemeColors();

    const labels = points.map(point =>
        point.time.replace("T", " ").slice(5, 16)
    );

    const values = points.map(point =>
        point.requestCount ?? 0
    );

    trafficTrendChart = new Chart(context, {

        type: "line",

        data: {
            labels,

            datasets: [{
                label: "API Requests",

                data: values,

                borderColor: colors.average,

                backgroundColor:
                    getComputedStyle(document.documentElement)
                        .getPropertyValue("--accent-soft").trim(),

                fill: true,

                tension: 0.35,

                pointRadius: 5,

                pointHoverRadius: 7,

                borderWidth: 3
            }]
        },

        options: {
            responsive: true,
            maintainAspectRatio: false,

            plugins: {
                legend: {
                    labels: {
                        color: colors.text,
                        usePointStyle: true
                    }
                }
            },

            scales: {
                x: {
                    ticks: {
                        color: colors.text
                    },
                    grid: {
                        color: colors.grid
                    }
                },

                y: {
                    beginAtZero: true,

                    ticks: {
                        precision: 0,
                        color: colors.text
                    },

                    grid: {
                        color: colors.grid
                    }
                }
            }
        }
    });
}


function renderRouteTrafficChart(routes) {

    const context = document.getElementById("routeTrafficChart");

    if (routeTrafficChart) {
        routeTrafficChart.destroy();
    }

    const colors = getThemeColors();

    routeTrafficChart = new Chart(context, {

        type: "doughnut",

        data: {
            labels: routes.map(route => route.routeId),

            datasets: [{
                label: "Requests",

                data: routes.map(route => route.totalRequests ?? 0),

                backgroundColor: [
                    colors.average,
                    colors.success,
                    colors.client,
                    colors.server,
                    colors.max
                ],

                borderWidth: 3,

                borderColor: getComputedStyle(
                    document.documentElement
                ).getPropertyValue("--surface").trim(),

                hoverOffset: 6
            }]
        },

        options: {
            responsive: true,
            maintainAspectRatio: false,

            plugins: {
                legend: {
                    position: "bottom",

                    labels: {
                        color: colors.text,
                        padding: 16,
                        usePointStyle: true
                    }
                }
            }
        }
    });
}


function renderRouteTrafficTable(routes) {

    const table = document.getElementById("routeTrafficTable");

    if (!routes.length) {
        table.innerHTML = `
            <tr>
                <td colspan="3" class="empty">
                    No route traffic data available.
                </td>
            </tr>
        `;
        return;
    }

    table.innerHTML = routes.map(route => `
        <tr>
            <td>${escapeHtml(route.routeId)}</td>

            <td>${route.totalRequests ?? 0}</td>

            <td>
                ${Number(route.trafficPercentage ?? 0).toFixed(2)}%
            </td>
        </tr>
    `).join("");
}


function renderTrafficAlerts(alerts) {

    const alertsList = document.getElementById("trafficAlertsList");

    const alertCount = document.getElementById("trafficAlertCount");

    alertCount.textContent =
        `${alerts.length} ${alerts.length === 1 ? "Alert" : "Alerts"}`;

    if (!alerts.length) {

        alertsList.innerHTML = `
            <div class="health-alert-empty">
                No unusual traffic spikes detected.
            </div>
        `;

        return;
    }

    alertsList.innerHTML = alerts.map(alert => {

        const severity =
            String(alert.severity ?? "WARNING").toLowerCase() === "critical"
                ? "critical"
                : "warning";

        return `
            <div class="health-alert-item ${severity}">

                <div class="health-alert-content">

                    <strong>Unusual Traffic Spike</strong>

                    <span>Route: ${escapeHtml(alert.routeId)}</span>

                    <p>${escapeHtml(alert.message)}</p>

                    <p>Requests detected: ${alert.requestCount ?? 0}</p>

                </div>

                <span class="health-alert-badge ${severity}">
                    ${severity.toUpperCase()}
                </span>

            </div>
        `;
    }).join("");
}


function renderTrafficAnalytics(data) {

    updateTrafficSummary(data);

    renderTrafficTrendChart(data.trafficTrend ?? []);

    renderRouteTrafficChart(data.routeTraffic ?? []);

    renderRouteTrafficTable(data.routeTraffic ?? []);

    renderTrafficAlerts(data.trafficAlerts ?? []);
}

/* HTML safety */

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}


function escapeCsvValue(value) {
    let text = String(value ?? "");

    // Protect spreadsheet users from CSV formula injection.
    if (/^[=+\-@\t\r]/.test(text)) {
        text = "'" + text;
    }

    return `"${text.replace(/"/g, '""')}"`;
}

function exportRequestEvents() {
    if (!currentEvents.length) {
        alert("No request records available for the selected date range.");
        return;
    }

    const headers = [
        "Timestamp",
        "Route ID",
        "Method",
        "Request Path",
        "Response Status",
        "Response Time (ms)",
        "Client IP",
        "User Agent"
    ];

    const rows = currentEvents.map(event => [
        event.timestamp,
        event.routeId,
        event.requestMethod,
        event.requestPath,
        event.responseStatus,
        event.responseTimeMs,
        event.clientIp,
        event.userAgent
    ]);

    const csvContent = [
        headers.map(escapeCsvValue).join(","),
        ...rows.map(row => row.map(escapeCsvValue).join(","))
    ].join("\r\n");

    // UTF-8 BOM improves compatibility with Excel.
    const blob = new Blob(
        ["\uFEFF" + csvContent],
        { type: "text/csv;charset=utf-8;" }
    );

    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    const date = new Date().toISOString().slice(0, 10);

    link.href = url;
    link.download = `gatekeeper-analytics-${date}.csv`;

    document.body.appendChild(link);
    link.click();
    link.remove();

    URL.revokeObjectURL(url);
}

/* Dashboard loading */

async function loadDashboard() {
    const applyButton =
        document.getElementById("applyDateFilterButton");

    try {
        const { startDate, endDate } = getDateRange();

        applyButton.disabled = true;
        applyButton.textContent = "Loading...";

		const [
		    summary,
		    performance,
		    routes,
		    events,
		    traffic
		] = await Promise.all([
		    fetchAnalytics("summary", startDate, endDate),
		    fetchAnalytics("performance", startDate, endDate),
		    fetchAnalytics("routes", startDate, endDate),
		    fetchAnalytics("events", startDate, endDate),
		    fetchAnalytics("traffic", startDate, endDate)
		]);

		currentEvents = events;

		updateSummary(summary);
		updatePerformance(performance);

		renderRouteTable(routes);
		renderRequestTable(events);

		renderHealthTable(routes);
		renderHealthAlerts(routes);

		renderStatusChart(summary);
		renderPerformanceChart(performance);

		renderTrafficAnalytics(traffic);

		resizeCharts();

    } catch (error) {
        console.error("Dashboard loading failed:", error);

        if (error.message !== "Authentication required.") {
            document.getElementById("routeTable").innerHTML =
                `<tr><td colspan="5" class="empty">${escapeHtml(error.message)}</td></tr>`;
				document.getElementById("healthTable").innerHTML =
				    `<tr><td colspan="5" class="empty">${escapeHtml(error.message)}</td></tr>`;
            document.getElementById("requestTable").innerHTML =
                `<tr><td colspan="5" class="empty">${escapeHtml(error.message)}</td></tr>`;
        }

    } finally {
        applyButton.disabled = false;
        applyButton.textContent = "Apply Filter";
    }
}

/* Application initialization */

document.addEventListener("DOMContentLoaded", () => {
    initializeTheme();
    initializeSidebar();
    initializeDateFilter();

    document.getElementById("themeToggleButton")
        .addEventListener("click", toggleTheme);
		document.getElementById("exportCsvButton")
		    .addEventListener("click", exportRequestEvents);

    requestAuthentication();
});
