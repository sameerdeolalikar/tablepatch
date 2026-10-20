"use strict";
const $ = (id) => document.getElementById(id);
let model = null,
  decisions = new Map(),
  page = 0,
  revision = 0;
function status(message) {
  $("status").textContent = message;
  $("status").hidden = !message;
}
function fail(error) {
  $("error").textContent = error.message;
  $("error").hidden = false;
}
function invalidate() {
  revision++;
  model = null;
  $("review").hidden = true;
  $("stats").hidden = true;
  $("empty").hidden = false;
  $("export-csv").disabled = $("export-patch").disabled = true;
  $("decision-count").textContent = "Compare again to review these edits";
  status("");
}
async function api(before, after, key) {
  const response = await fetch("/api/compare", {
    method: "POST",
    headers: { "X-Requested-With": "TablePatch" },
    body: new URLSearchParams({ before, after, key }),
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "Comparison failed.");
  return data;
}
function csv(rows) {
  return (
    rows
      .map((row) =>
        row
          .map((v) =>
            /[",\r\n]/.test(v) ? '"' + v.replaceAll('"', '""') + '"' : v,
          )
          .join(","),
      )
      .join("\n") + "\n"
  );
}
function resolved() {
  return [
    model.header,
    ...model.records.flatMap((r) => {
      const row = decisions.get(r.key) === "before" ? r.before : r.after;
      return row ? [row] : [];
    }),
  ];
}
function summary() {
  const kept = [...decisions.values()].filter((v) => v === "before").length;
  $("decision-count").textContent =
    `${resolved().length - 1} output records · ${kept} original selections`;
}
function fieldCell(row, other) {
  const cell = document.createElement("td");
  if (!row) {
    cell.textContent = "— No record";
    cell.className = "muted";
    return cell;
  }
  row.forEach((value, i) => {
    const field = document.createElement("div");
    field.className = "field" + (other && value !== other[i] ? " changed" : "");
    const label = document.createElement("b");
    label.textContent = model.header[i];
    const text = document.createElement("span");
    text.textContent = value || "(empty)";
    field.append(label, text);
    cell.append(field);
  });
  return cell;
}
function render() {
  if (!model) return;
  const term = $("search").value.toLowerCase(),
    filter = $("filter").value;
  const records = model.records.filter(
    (r) =>
      (filter === "all" ||
        (filter === "changes" ? r.type !== "unchanged" : r.type === filter)) &&
      [r.key, ...(r.before || []), ...(r.after || [])].some((v) =>
        v.toLowerCase().includes(term),
      ),
  );
  const pages = Math.max(1, Math.ceil(records.length / 50));
  page = Math.min(page, pages - 1);
  $("rows").replaceChildren();
  records.slice(page * 50, (page + 1) * 50).forEach((r) => {
    const tr = document.createElement("tr"),
      key = document.createElement("td"),
      tag = document.createElement("span"),
      name = document.createElement("div");
    tag.className = "tag " + r.type;
    tag.textContent = r.type;
    name.textContent = r.key;
    key.append(tag, name);
    const action = document.createElement("td"),
      select = document.createElement("select");
    select.setAttribute("aria-label", `Version for ${r.key}`);
    [
      ["after", r.after ? "Incoming" : "Remove record"],
      ["before", r.before ? "Original" : "Exclude addition"],
    ].forEach(([value, label]) => select.add(new Option(label, value)));
    select.value = decisions.get(r.key);
    select.addEventListener("change", () => {
      decisions.set(r.key, select.value);
      summary();
    });
    action.append(select);
    tr.append(
      key,
      fieldCell(r.before, r.after),
      fieldCell(r.after, r.before),
      action,
    );
    $("rows").append(tr);
  });
  $("result-count").textContent = records.length
    ? `${records.length} matching records · choose which version to keep`
    : "No records match your filters.";
  $("page").textContent = `Page ${page + 1} of ${pages}`;
  $("previous").disabled = page === 0;
  $("next").disabled = page === pages - 1;
  summary();
}
async function compare() {
  const current = ++revision;
  $("error").hidden = true;
  $("compare").disabled = true;
  $("export-csv").disabled = $("export-patch").disabled = true;
  status("Comparing datasets…");
  const before = $("before").value,
    after = $("after").value,
    key = $("key").value;
  try {
    const data = await api(before, after, key);
    if (current !== revision) return;
    const header = data.before[0],
      keyIndex = header.indexOf(key),
      a = new Map(data.before.slice(1).map((r) => [r[keyIndex], r])),
      b = new Map(data.after.slice(1).map((r) => [r[keyIndex], r]));
    const keys = [...new Set([...b.keys(), ...a.keys()])];
    model = {
      header,
      before,
      key,
      records: keys.map((k) => ({
        key: k,
        before: a.get(k),
        after: b.get(k),
        type: !a.has(k)
          ? "added"
          : !b.has(k)
            ? "removed"
            : JSON.stringify(a.get(k)) === JSON.stringify(b.get(k))
              ? "unchanged"
              : "modified",
      })),
    };
    decisions = new Map(keys.map((k) => [k, "after"]));
    page = 0;
    ["modified", "added", "removed", "unchanged"].forEach(
      (type) =>
        ($("count-" + type).textContent = model.records.filter(
          (r) => r.type === type,
        ).length),
    );
    $("empty").hidden = true;
    $("review").hidden = false;
    $("stats").hidden = false;
    $("export-csv").disabled = $("export-patch").disabled = false;
    render();
    status(
      "Comparison ready. Incoming values are selected by default; review and change any decision.",
    );
  } catch (error) {
    if (current === revision) {
      invalidate();
      fail(error);
    }
  } finally {
    $("compare").disabled = false;
  }
}
function download(name, text) {
  const url = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function exportResult(patch) {
  if (!model) return;
  const current = revision,
    output = csv(resolved());
  $("error").hidden = true;
  try {
    const checked = await api(model.before, output, model.key);
    if (current !== revision) return;
    download(
      patch ? "tablepatch-changes.csv" : "tablepatch-resolved.csv",
      patch ? checked.patch : output,
    );
    status("Export validated and sent to your browser downloads.");
  } catch (error) {
    fail(error);
  }
}
["before", "after", "key"].forEach((id) =>
  $(id).addEventListener("input", invalidate),
);
["before", "after"].forEach((id) =>
  $(id + "-file").addEventListener("change", async (event) => {
    try {
      const file = event.target.files[0];
      if (!file) return;
      if (file.size > 1_000_000)
        throw new Error(
          "Choose a CSV smaller than 1 MB for the visual workspace.",
        );
      $(id).value = await file.text();
      invalidate();
    } catch (error) {
      fail(error);
    }
  }),
);
$("compare").addEventListener("click", compare);
$("demo").addEventListener("click", () => {
  $("before").value =
    "sku,name,quantity\nA1,Notebook,12\nB2,Sketch pencil,24\nC3,Canvas bag,8\nD4,Paper clips,50\n";
  $("after").value =
    "sku,name,quantity\nA1,Notebook,18\nB2,Sketch pencil,24\nD4,Paper clips,40\nE5,Index cards,30\n";
  $("key").value = "sku";
  invalidate();
  compare();
});
["search", "filter"].forEach((id) =>
  $(id).addEventListener("input", () => {
    page = 0;
    render();
  }),
);
$("previous").addEventListener("click", () => {
  page--;
  render();
});
$("next").addEventListener("click", () => {
  page++;
  render();
});
$("all-incoming").addEventListener("click", () => {
  if (model) model.records.forEach((r) => decisions.set(r.key, "after"));
  render();
});
$("all-original").addEventListener("click", () => {
  if (model) model.records.forEach((r) => decisions.set(r.key, "before"));
  render();
});
$("export-csv").addEventListener("click", () => exportResult(false));
$("export-patch").addEventListener("click", () => exportResult(true));
