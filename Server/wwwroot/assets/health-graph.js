(function () {
    const apiKeyStorageKey = "hc_api_key";

    const metricInput = document.getElementById("metric");
    const dateInput = document.getElementById("date");
    const apiKeyInput = document.getElementById("apiKey");
    const saveKeyButton = document.getElementById("saveKey");
    const clearKeyButton = document.getElementById("clearKey");
    const showGraphButton = document.getElementById("showGraph");
    const keyStatus = document.getElementById("keyStatus");
    const queryStatus = document.getElementById("queryStatus");
    const plot = document.getElementById("plot");

    function sessionKey() {
        return (sessionStorage.getItem(apiKeyStorageKey) || "").trim();
    }

    function formKey() {
        return (apiKeyInput.value || sessionKey()).trim();
    }

    function saveSessionKey() {
        const key = apiKeyInput.value.trim();
        if (!key) {
            sessionStorage.removeItem(apiKeyStorageKey);
            keyStatus.textContent = "API key cleared for this session.";
            return;
        }

        sessionStorage.setItem(apiKeyStorageKey, key);
        keyStatus.textContent = "API key ready for this session.";
    }

    function clearSessionKey() {
        sessionStorage.removeItem(apiKeyStorageKey);
        localStorage.removeItem(apiKeyStorageKey);
        apiKeyInput.value = "";
        keyStatus.textContent = "API key cleared.";
    }

    function restoreSessionKey() {
        const persistedKey = localStorage.getItem(apiKeyStorageKey);
        if (persistedKey && !sessionKey()) {
            sessionStorage.setItem(apiKeyStorageKey, persistedKey);
            localStorage.removeItem(apiKeyStorageKey);
        }

        const key = sessionKey();
        if (key) {
            apiKeyInput.value = key;
            keyStatus.textContent = "API key ready for this session.";
        }
    }

    function setQueryStatus(message) {
        queryStatus.textContent = message;
    }

    function setBusy(busy) {
        showGraphButton.disabled = busy;
        saveKeyButton.disabled = busy;
        clearKeyButton.disabled = busy;
    }

    function localDate() {
        if (dateInput.value) {
            return dateInput.value;
        }

        dateInput.valueAsDate = new Date();
        return dateInput.value;
    }

    async function fetchRows(metric, date, apiKey) {
        const tzOffsetMinutes = -new Date().getTimezoneOffset();
        const query = new URLSearchParams({
            metric,
            date,
            tzOffsetMinutes: String(tzOffsetMinutes)
        });

        const response = await fetch(`/samples/dayLocal?${query}`, {
            headers: { "X-API-Key": apiKey }
        });

        if (!response.ok) {
            const body = await response.text();
            throw new Error(`Query failed (${response.status}). ${body}`);
        }

        return response.json();
    }

    function drawRows(metric, rows) {
        const x = rows.map(row => new Date(row.t * 1000));
        const y = rows.map(row => row.vF ?? row.vI ?? 0);

        Plotly.newPlot(plot, [{ x, y, mode: "lines+markers", name: metric }], {
            margin: { t: 20 },
            xaxis: { title: "Time (local)" },
            yaxis: { title: metric }
        }, { responsive: true });
    }

    async function showGraph() {
        if (typeof Plotly === "undefined") {
            setQueryStatus("Chart library did not load. Check network access to the Plotly CDN.");
            return;
        }

        const metric = metricInput.value;
        const date = localDate();
        const apiKey = formKey();

        if (!date) {
            setQueryStatus("Pick a date before querying.");
            return;
        }

        if (!apiKey) {
            setQueryStatus("Enter an API key before querying.");
            return;
        }

        setBusy(true);
        setQueryStatus(`Loading ${metric} for ${date}...`);

        try {
            const rows = await fetchRows(metric, date, apiKey);
            drawRows(metric, rows);
            setQueryStatus(rows.length === 0
                ? `No ${metric} rows for ${date}.`
                : `Plotted ${rows.length} ${metric} rows for ${date}.`);
        } catch (error) {
            setQueryStatus(error.message || "Query failed.");
        } finally {
            setBusy(false);
        }
    }

    document.addEventListener("DOMContentLoaded", () => {
        restoreSessionKey();
        localDate();
        saveKeyButton.addEventListener("click", saveSessionKey);
        clearKeyButton.addEventListener("click", clearSessionKey);
        showGraphButton.addEventListener("click", showGraph);
    });
})();
