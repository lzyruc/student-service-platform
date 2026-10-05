// Incremental UTF-8 + NDJSON. A Chinese character may span multiple network chunks.
function createReader(onEvent) {
  let carry = [], text = '', count = 0, bytes = 0, terminal = null;
  function line(value) {
    if (!value.trim()) return;
    if (++count > 200) throw new Error('执行记录过长，请重新发起分析。');
    const event = JSON.parse(value);
    if (!event || !['progress', 'evidence', 'result', 'error'].includes(event.type)) throw new Error('执行响应格式异常。');
    if (terminal) throw new Error('收到重复的结束消息。');
    if (event.type === 'result' || event.type === 'error') terminal = event;
    if (typeof onEvent === 'function') onEvent(event);
  }
  function accept(value) {
    text += value;
    let next;
    while ((next = text.indexOf('\n')) >= 0) { line(text.slice(0, next)); text = text.slice(next + 1); }
  }
  function feed(data) {
    if (typeof data === 'string') { bytes += data.length * 3; if (bytes > 2 * 1024 * 1024) throw new Error('分析响应过大。'); accept(data); return; }
    const input = data instanceof ArrayBuffer ? new Uint8Array(data) : data;
    if (!input || typeof input.length !== 'number') throw new Error('执行响应格式异常。');
    bytes += input.length; if (bytes > 2 * 1024 * 1024) throw new Error('分析响应过大。');
    const all = carry.concat(Array.from(input)); carry = []; let out = '';
    for (let i = 0; i < all.length;) {
      const first = all[i]; const size = first < 128 ? 1 : first >= 194 && first <= 223 ? 2 : first >= 224 && first <= 239 ? 3 : first >= 240 && first <= 244 ? 4 : 0;
      if (!size) throw new Error('执行响应编码异常。');
      if (i + size > all.length) { carry = all.slice(i); break; }
      let code = size === 1 ? first : first & (size === 2 ? 31 : size === 3 ? 15 : 7);
      for (let j = 1; j < size; j++) { if ((all[i+j] & 192) !== 128) throw new Error('执行响应编码异常。'); code = (code << 6) | (all[i+j] & 63); }
      if ((size === 2 && code < 128) || (size === 3 && code < 2048) || (size === 4 && code < 65536) || code > 1114111 || (code >= 55296 && code <= 57343)) throw new Error('执行响应编码异常。');
      out += String.fromCodePoint(code); i += size;
    }
    accept(out);
  }
  function finish() {
    if (carry.length) throw new Error('执行响应未完整接收。');
    if (text.trim()) line(text); text = '';
    if (!terminal) throw new Error('分析连接已中断，请重新尝试。');
    if (terminal.type === 'error') throw { code: terminal.data.code, statusCode: terminal.data.code, message: terminal.data.message };
    return terminal.data;
  }
  return { feed, finish };
}
module.exports = { createReader };
