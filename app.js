"use strict";

// Single-page UI for the Employee Management System.
// Talks to the REST API with a JWT kept in sessionStorage (cleared when the tab closes).
// All dynamic content is inserted with textContent / createTextNode, never innerHTML,
// so employee-supplied data cannot inject markup.
(() => {
  const TOKEN_KEY = "ems.token";

  const state = {
    token: sessionStorage.getItem(TOKEN_KEY),
    me: null,
    page: 0,
    totalPages: 0,
    editingId: null,
    attendanceTabLoaded: false,
  };

  // ------------------------------------------------------------------ helpers

  const $ = (selector) => document.querySelector(selector);

  function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
      if (key === "class") node.className = value;
      else if (key.startsWith("on")) node.addEventListener(key.slice(2).toLowerCase(), value);
      else node.setAttribute(key, value);
    }
    for (const child of children) {
      node.append(child instanceof Node ? child : document.createTextNode(child == null ? "" : String(child)));
    }
    return node;
  }

  function isoDate(date) {
    const pad = (n) => String(n).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  }

  function formatMoney(value) {
    if (value == null) return "—";
    return Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  let toastTimer;
  function toast(message, isError = false) {
    const node = $("#toast");
    node.textContent = message;
    node.className = isError ? "show error" : "show";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      node.className = "";
    }, 3500);
  }

  function showError(node, message) {
    node.textContent = message;
    node.hidden = false;
  }

  function hideError(node) {
    node.textContent = "";
    node.hidden = true;
  }

  function errorText(error) {
    if (error.fieldErrors && Object.keys(error.fieldErrors).length) {
      return Object.entries(error.fieldErrors)
        .map(([field, message]) => `${field}: ${message}`)
        .join("; ");
    }
    return error.message || "Something went wrong.";
  }

  async function api(path, { method = "GET", body } = {}) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (state.token) headers.Authorization = `Bearer ${state.token}`;

    const response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (response.status === 401 && state.token) {
      logout("Your session has expired. Please sign in again.");
      throw new Error("Not signed in.");
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = null;
      }
    }
    if (!response.ok) {
      const error = new Error((data && data.message) || `Request failed (${response.status}).`);
      error.fieldErrors = data && data.fieldErrors;
      throw error;
    }
    return data;
  }

  // -------------------------------------------------------------- session/views

  function showOnly(viewId) {
    for (const id of ["login-view", "admin-view", "employee-view"]) {
      $(`#${id}`).hidden = id !== viewId;
    }
    $("#session").hidden = viewId === "login-view";
  }

  function logout(message) {
    state.token = null;
    state.me = null;
    state.attendanceTabLoaded = false;
    sessionStorage.removeItem(TOKEN_KEY);
    showOnly("login-view");
    if (message) toast(message, true);
  }

  async function startApp() {
    state.me = await api("/api/me");
    $("#whoami").textContent = `${state.me.fullName} (${state.me.role})`;
    if (state.me.role === "ADMIN") {
      showOnly("admin-view");
      selectTab("employees");
      await loadEmployees();
    } else {
      showOnly("employee-view");
      renderProfile(state.me);
      await loadMyAttendance();
    }
  }

  $("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const errorNode = $("#login-error");
    hideError(errorNode);
    try {
      state.token = null;
      const data = await api("/api/auth/login", {
        method: "POST",
        body: {
          username: $("#login-username").value.trim(),
          password: $("#login-password").value,
        },
      });
      state.token = data.token;
      sessionStorage.setItem(TOKEN_KEY, data.token);
      $("#login-form").reset();
      await startApp();
    } catch (error) {
      if (!state.token) showError(errorNode, errorText(error));
    }
  });

  $("#logout").addEventListener("click", () => logout());

  // ------------------------------------------------------------------- admin

  function selectTab(name) {
    document.querySelectorAll(".tab").forEach((tab) => {
      tab.classList.toggle("active", tab.dataset.tab === name);
    });
    $("#tab-employees").hidden = name !== "employees";
    $("#tab-attendance").hidden = name !== "attendance";
  }

  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", async () => {
      selectTab(tab.dataset.tab);
      if (tab.dataset.tab === "attendance" && !state.attendanceTabLoaded) {
        await initAttendanceTab();
      }
    });
  });

  function employeeRow(employee) {
    return el(
      "tr",
      {},
      el("td", {}, employee.fullName),
      el("td", {}, employee.username),
      el("td", {}, employee.department || "—"),
      el("td", {}, employee.jobTitle || "—"),
      el("td", {}, el("span", { class: `badge badge-${employee.role.toLowerCase()}` }, employee.role)),
      el(
        "td",
        {},
        el(
          "span",
          { class: employee.enabled ? "badge badge-active" : "badge badge-disabled" },
          employee.enabled ? "Active" : "Disabled"
        )
      ),
      el(
        "td",
        { class: "actions" },
        el("button", { type: "button", class: "link", onClick: () => openDialog(employee) }, "Edit"),
        el("button", { type: "button", class: "link danger", onClick: () => removeEmployee(employee) }, "Delete")
      )
    );
  }

  async function loadEmployees() {
    const params = new URLSearchParams({ page: state.page, size: 10, sortBy: "fullName", direction: "asc" });
    const search = $("#search").value.trim();
    const department = $("#department-filter").value.trim();
    if (search) params.set("search", search);
    if (department) params.set("department", department);

    try {
      const data = await api(`/api/employees?${params}`);
      state.totalPages = data.totalPages;
      const body = $("#employee-rows");
      body.replaceChildren(...data.content.map(employeeRow));
      $("#employee-empty").hidden = data.content.length > 0;
      $("#page-info").textContent = data.totalPages
        ? `Page ${data.page + 1} of ${data.totalPages} · ${data.totalElements} employees`
        : "";
      $("#prev-page").disabled = data.page <= 0;
      $("#next-page").disabled = data.page + 1 >= data.totalPages;
    } catch (error) {
      toast(errorText(error), true);
    }
  }

  let searchTimer;
  function reloadFromFirstPage() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      state.page = 0;
      loadEmployees();
    }, 250);
  }
  $("#search").addEventListener("input", reloadFromFirstPage);
  $("#department-filter").addEventListener("input", reloadFromFirstPage);
  $("#prev-page").addEventListener("click", () => {
    if (state.page > 0) {
      state.page -= 1;
      loadEmployees();
    }
  });
  $("#next-page").addEventListener("click", () => {
    if (state.page + 1 < state.totalPages) {
      state.page += 1;
      loadEmployees();
    }
  });

  // ---- add / edit dialog

  const dialog = $("#employee-dialog");

  function openDialog(employee) {
    const editing = Boolean(employee);
    state.editingId = editing ? employee.id : null;
    hideError($("#dialog-error"));
    $("#employee-form").reset();
    $("#dialog-title").textContent = editing ? "Edit employee" : "Add employee";

    $("#username-field").hidden = editing;
    $("#password-field").hidden = editing;
    $("#reset-field").hidden = !editing;
    $("#enabled-field").hidden = !editing;
    // Hidden required inputs would block form submission, so only require them when visible.
    $("#f-username").required = !editing;
    $("#f-password").required = !editing;

    if (editing) {
      $("#f-fullName").value = employee.fullName;
      $("#f-email").value = employee.email;
      $("#f-department").value = employee.department || "";
      $("#f-jobTitle").value = employee.jobTitle || "";
      $("#f-salary").value = employee.salary == null ? "" : employee.salary;
      $("#f-joinDate").value = employee.joinDate;
      $("#f-role").value = employee.role;
      $("#f-enabled").checked = employee.enabled;
    } else {
      $("#f-joinDate").value = isoDate(new Date());
    }
    dialog.showModal();
  }

  $("#add-employee").addEventListener("click", () => openDialog(null));
  $("#dialog-cancel").addEventListener("click", () => dialog.close());

  $("#employee-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const salary = $("#f-salary").value;
    const body = {
      role: $("#f-role").value,
      fullName: $("#f-fullName").value.trim(),
      email: $("#f-email").value.trim(),
      department: $("#f-department").value.trim(),
      jobTitle: $("#f-jobTitle").value.trim(),
      salary: salary === "" ? null : Number(salary),
      joinDate: $("#f-joinDate").value,
    };

    try {
      if (state.editingId) {
        body.enabled = $("#f-enabled").checked;
        const newPassword = $("#f-newPassword").value;
        if (newPassword) body.newPassword = newPassword;
        await api(`/api/employees/${state.editingId}`, { method: "PUT", body });
      } else {
        body.username = $("#f-username").value.trim();
        body.password = $("#f-password").value;
        await api("/api/employees", { method: "POST", body });
      }
      dialog.close();
      toast("Employee saved.");
      state.attendanceTabLoaded = false; // refresh the employee dropdowns next time
      await loadEmployees();
    } catch (error) {
      showError($("#dialog-error"), errorText(error));
    }
  });

  async function removeEmployee(employee) {
    if (!window.confirm(`Delete ${employee.fullName}? Their attendance records will be deleted too.`)) return;
    try {
      await api(`/api/employees/${employee.id}`, { method: "DELETE" });
      toast("Employee deleted.");
      state.attendanceTabLoaded = false;
      if (state.page > 0 && $("#employee-rows").children.length === 1) state.page -= 1;
      await loadEmployees();
    } catch (error) {
      toast(errorText(error), true);
    }
  }

  // ---- attendance tab

  async function initAttendanceTab() {
    try {
      const data = await api("/api/employees?size=100&sortBy=fullName&direction=asc");
      const markOptions = data.content.map((e) => el("option", { value: e.id }, `${e.fullName} (${e.username})`));
      const filterOptions = [el("option", { value: "" }, "All employees"), ...markOptions.map((o) => o.cloneNode(true))];
      $("#mark-employee").replaceChildren(...markOptions);
      $("#filter-employee").replaceChildren(...filterOptions);

      const today = new Date();
      const monthAgo = new Date();
      monthAgo.setDate(today.getDate() - 30);
      $("#mark-date").value = isoDate(today);
      $("#mark-date").max = isoDate(today);
      $("#filter-from").value = isoDate(monthAgo);
      $("#filter-to").value = isoDate(today);

      state.attendanceTabLoaded = true;
      await loadAttendance();
    } catch (error) {
      toast(errorText(error), true);
    }
  }

  function attendanceRow(record) {
    return el(
      "tr",
      {},
      el("td", {}, record.date),
      el("td", {}, record.employeeName),
      el("td", {}, el("span", { class: `badge badge-${record.status.toLowerCase()}` }, record.status))
    );
  }

  function summaryText(summary) {
    return `Present ${summary.present} · Absent ${summary.absent} · Leave ${summary.leave} · ` +
      `Attendance ${summary.attendancePercentage}% (${summary.from} to ${summary.to})`;
  }

  async function loadAttendance() {
    const params = new URLSearchParams();
    const employeeId = $("#filter-employee").value;
    if (employeeId) params.set("employeeId", employeeId);
    if ($("#filter-from").value) params.set("from", $("#filter-from").value);
    if ($("#filter-to").value) params.set("to", $("#filter-to").value);

    try {
      const records = await api(`/api/attendance?${params}`);
      $("#attendance-rows").replaceChildren(...records.map(attendanceRow));
      $("#attendance-empty").hidden = records.length > 0;

      const summaryNode = $("#admin-summary");
      if (employeeId) {
        const summary = await api(`/api/attendance/summary?${params}`);
        summaryNode.textContent = summaryText(summary);
        summaryNode.hidden = false;
      } else {
        summaryNode.hidden = true;
      }
    } catch (error) {
      toast(errorText(error), true);
    }
  }

  $("#filter-form").addEventListener("submit", (event) => {
    event.preventDefault();
    loadAttendance();
  });

  $("#mark-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api("/api/attendance", {
        method: "POST",
        body: {
          employeeId: Number($("#mark-employee").value),
          date: $("#mark-date").value,
          status: $("#mark-status").value,
        },
      });
      toast("Attendance saved.");
      await loadAttendance();
    } catch (error) {
      toast(errorText(error), true);
    }
  });

  // ---------------------------------------------------------------- employee

  function renderProfile(me) {
    const rows = [
      ["Name", me.fullName],
      ["Username", me.username],
      ["Email", me.email],
      ["Department", me.department || "—"],
      ["Job title", me.jobTitle || "—"],
      ["Salary", formatMoney(me.salary)],
      ["Joined", me.joinDate],
    ];
    const list = $("#profile");
    list.replaceChildren();
    for (const [label, value] of rows) {
      list.append(el("dt", {}, label), el("dd", {}, value));
    }
  }

  async function loadMyAttendance() {
    try {
      const [records, summary] = await Promise.all([
        api("/api/me/attendance"),
        api("/api/me/attendance/summary"),
      ]);
      $("#my-attendance-rows").replaceChildren(
        ...records.map((r) =>
          el(
            "tr",
            {},
            el("td", {}, r.date),
            el("td", {}, el("span", { class: `badge badge-${r.status.toLowerCase()}` }, r.status))
          )
        )
      );
      $("#my-attendance-empty").hidden = records.length > 0;
      const summaryNode = $("#my-summary");
      summaryNode.textContent = summaryText(summary);
      summaryNode.hidden = records.length === 0;
    } catch (error) {
      toast(errorText(error), true);
    }
  }

  $("#check-in").addEventListener("click", async () => {
    try {
      await api("/api/me/attendance/check-in", { method: "POST" });
      toast("Checked in for today.");
      await loadMyAttendance();
    } catch (error) {
      toast(errorText(error), true);
    }
  });

  $("#password-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api("/api/me/password", {
        method: "PUT",
        body: {
          currentPassword: $("#current-password").value,
          newPassword: $("#new-password").value,
        },
      });
      $("#password-form").reset();
      toast("Password updated.");
    } catch (error) {
      toast(errorText(error), true);
    }
  });

  // ------------------------------------------------------------------- start

  if (state.token) {
    startApp().catch(() => {
      // 401 already routed to the login screen by api(); anything else falls back to it too.
      if (state.token) logout();
    });
  } else {
    showOnly("login-view");
  }
})();
