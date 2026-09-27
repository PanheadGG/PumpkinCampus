// 南瓜校园 · 教务系统课表插件模板
// ---------------------------------------------------------------
// 宿主约定：入口必须是 ES Module，并导出 getCourses(ctx)（可 async）。
// 宿主预置了 http / ksoup / storage / console，直接用即可。
// 完整开发文档：docs/plugin-development.md（API 速查：docs/plugin-api.md）
// 把下面的「教务系统」部分换成你学校的实现即可。
// ---------------------------------------------------------------

import {
  parseWeekRanges,
  parseDay,
  parseClock,
  fetchWithRetry,
  urlencode
} from "./utils/util.js";

/**
 * 同步入口：宿主每次「同步课表」调用一次。
 *
 * @param {object} ctx 见 docs/plugin-development.md：
 *   { pluginId, pluginName, hostVersionCode, schedule: {id, name}, config: {...} }
 * @returns {Promise<object[]>} 课程数组（字段见返回值注释）
 */
export async function getCourses(ctx) {
  const config = ctx.config || {};
  const base = String(config.base_url || "").replace(/\/+$/, "");
  const username = String(config.username || "");
  const password = String(config.password || "");
  const term = String(config.term || "");
  const retries = Number(config.retries || 0);

  if (!base) {
    throw new Error("请先在「课表设置 → 教务系统插件」里填写教务系统地址");
  }
  if (!username || !password) {
    throw new Error("请先在「课表设置 → 教务系统插件」里填写学号与密码");
  }

  console.log("开始同步:", ctx.schoolName || base, "学期:", term, "课表:", ctx.schedule.name);

  // ---------------------------------------------------------------
  // 1) 登录（示例为表单登录；按你的教务系统改成 JSON / 验证码等）
  //    同一次同步里 Cookie 会自动保持，登录一次即可继续访问
  // ---------------------------------------------------------------
  const login = await fetchWithRetry(
    () =>
      http.post(
        base + "/login",
        // QuickJS 没有 URLSearchParams：用 encodeURIComponent 自己拼表单体
        urlencode({ username, password }),
        { contentType: "application/x-www-form-urlencoded" }
      ),
    retries
  );
  if (!login.ok) {
    throw new Error("登录请求失败：HTTP " + login.status + (login.error ? "（" + login.error + "）" : ""));
  }
  // 真实系统里登录失败通常是 200 + 页面提示，按需判断：
  // if (login.body.includes("密码错误")) throw new Error("学号或密码错误");

  // ---------------------------------------------------------------
  // 2) 拉取课表数据（示例同时给出 JSON 接口与 HTML 页面两种写法）
  // ---------------------------------------------------------------

  // —— 写法 A：教务提供 JSON 接口 ——
  // const resp = await http.get(base + "/api/schedule?term=" + encodeURIComponent(term));
  // if (!resp.ok) throw new Error("拉取课表失败：HTTP " + resp.status);
  // const data = JSON.parse(resp.body);
  // return data.lessons.map((it) => ({
  //   name: it.name,
  //   teacher: it.teacher,
  //   classroom: it.room,
  //   dayIndex: parseDay(it.day),
  //   lessonStartIndex: it.startOrder - 1,
  //   lessonCount: it.endOrder - it.startOrder + 1,
  //   weekIndices: parseWeekRanges(it.weeks) // 教务的 "1-16" 1 基写法 → 0 基
  // }));

  // —— 写法 B：教务是 HTML 页面（ksoup 解析） ——
  const page = await http.get(base + "/students/schedule?term=" + encodeURIComponent(term));
  if (!page.ok) {
    throw new Error("拉取课表页面失败：HTTP " + page.status + (page.error ? "（" + page.error + "）" : ""));
  }

  const doc = ksoup.parse(page.body);
  const rows = doc.select("table.schedule tbody tr"); // ← 换成你学校的 CSS 选择器
  if (rows.length === 0) {
    // 选择器没命中：把页面前 200 个字符打进日志，方便调试
    console.warn("没有匹配到课程行，页面开头：", page.body.slice(0, 200));
    return [];
  }

  const courses = [];
  for (const row of rows) {
    const name = row.selectFirst("td.course")?.text?.trim();
    if (!name) continue;

    courses.push({
      name,
      teacher: row.selectFirst("td.teacher")?.text?.trim() ?? "",
      classroom: row.selectFirst("td.room")?.text?.trim() ?? "",
      dayIndex: parseDay(row.attr("data-day") ?? row.selectFirst("td.day")?.text ?? ""),
      lessonStartIndex: parseInt(row.attr("data-start") ?? "1", 10) - 1,
      lessonCount: Math.max(1, parseInt(row.attr("data-len") ?? "2", 10)),
      weekIndices: parseWeekRanges(row.attr("data-weeks") ?? row.selectFirst("td.weeks")?.text ?? "") ?? []
    });
  }

  console.log("解析到课程:", courses.length, "门");

  // ---------------------------------------------------------------
  // 3) 可选：把成功结果缓存到 storage（按 课表 隔离），
  //    下次同步可以先用缓存兜底 / 对比
  // ---------------------------------------------------------------
  if (config.use_cache) {
    storage.set("schedule_cache", { term, time: Date.now(), courses });
  }

  return courses;
}
