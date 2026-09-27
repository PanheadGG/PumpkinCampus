// 周次 / 时间等纯工具函数：可以在自己的插件里随意增删改。
// 通过相对路径导入：import { parseWeekRanges } from "./utils/util.js";

/**
 * 把教务里常见的周次写法解析成 0 基周索引数组（0 = 第 1 教学周）。
 *
 *   parseWeekRanges("1-8,10")   => [0,1,2,3,4,5,6,7,9]
 *   parseWeekRanges("1,3,5-6")  => [0,2,4,5]
 *   parseWeekRanges("全学期") / parseWeekRanges("") => null（调用方决定默认值）
 *
 * @param {string} text 周次文本，如 "1-16"、"1-8,10"、"16周"
 * @param {number} total 学期总周数（无法解析时配合 fallback 使用）
 * @returns {number[]|null} 0 基周索引；解析不出返回 null
 */
export function parseWeekRanges(text, total = 18) {
  if (typeof text !== "string") return null;
  const cleaned = text.replace(/\s|周|星期/g, "");
  if (!cleaned || cleaned.includes("全")) {
    return range(0, total);
  }
  const result = [];
  for (const part of cleaned.split(",")) {
    if (!part) continue;
    const m = part.match(/^(\d+)-(\d+)$/);
    if (m) {
      const from = parseInt(m[1], 10);
      const to = parseInt(m[2], 10);
      for (let w = from; w <= to; w++) result.push(w - 1);
    } else if (/^\d+$/.test(part)) {
      result.push(parseInt(part, 10) - 1);
    } else {
      return null; // 含无法识别的片段
    }
  }
  return result;
}

/** [from, to) 的整数序列。 */
export function range(from, to) {
  const out = [];
  for (let i = from; i < to; i++) out.push(i);
  return out;
}

/**
 * urlencode 表单体：`a=1&b=2`。
 * QuickJS 里没有 `URLSearchParams`，请求表单（`application/x-www-form-urlencoded`）
 * 用这个函数拼，例如：`http.post(url, urlencode({ username, password }), { contentType: ... })`。
 */
export function urlencode(params) {
  const parts = [];
  for (const key of Object.keys(params)) {
    const value = params[key];
    if (value === undefined || value === null) continue;
    parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(value)));
  }
  return parts.join("&");
}

/** 星期文本 → dayIndex（0=周日）：支持 "周一"、"星期一"、"礼拜一"、"1"~"7"（1=周一）。 */
export function parseDay(text) {
  if (typeof text === "number") {
    return text >= 1 && text <= 7 ? text % 7 : -1;
  }
  const s = String(text ?? "").trim();
  const cn = { "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "日": 0, "天": 0 };
  const m = s.match(/周|星期|礼拜\s*(.)/);
  const ch = m ? m[1] : s;
  if (ch in cn) return cn[ch];
  if (/^[1-7]$/.test(s)) return parseInt(s, 10) % 7;
  return -1;
}

/** "8:00" → 当天 0 点起分钟数（失败返回 -1）。 */
export function parseClock(text) {
  const m = String(text ?? "").match(/(\d{1,2}):(\d{2})/);
  if (!m) return -1;
  return parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
}

/**
 * 带重试的网络请求：检查 resp.ok，失败按配置重试。
 * 注意：网络错误不抛异常，靠 resp.ok / resp.error 判断。
 */
export async function fetchWithRetry(makeRequest, retries = 2) {
  let last = null;
  for (let i = 0; i <= retries; i++) {
    last = await makeRequest();
    if (last.ok) return last;
  }
  return last;
}

/** 从可能带 BOM/GBK 乱码痕迹的文本里截取第一个 { 或 [ 开头的 JSON 段并解析。 */
export function parseJsonLoose(text) {
  const s = String(text ?? "").replace(/^\uFEFF/, "").trim();
  try {
    return JSON.parse(s);
  } catch (e) {
    const start = Math.min(
      ...["{", "["].map((c) => {
        const i = s.indexOf(c);
        return i < 0 ? Number.MAX_SAFE_INTEGER : i;
      })
    );
    if (start === Number.MAX_SAFE_INTEGER) throw e;
    return JSON.parse(s.slice(start));
  }
}
