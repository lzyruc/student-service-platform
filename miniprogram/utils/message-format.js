// Render a safe, small Markdown subset as WXML rich-text nodes, never arbitrary HTML.
function inline(text) {
  return String(text).split(/(\*\*[^*]+\*\*)/g).filter(Boolean).map(part=>part.startsWith('**') && part.endsWith('**')
    ? {name:'strong',attrs:{style:'font-weight:600;color:#26385a'},children:[{type:'text',text:part.slice(2,-2)}]}
    : {type:'text',text:part});
}
function formatMessage(content) {
  return String(content || '').split('\n').map(line=>{
    const heading=line.match(/^(#{1,3})\s+(.+)$/);const bullet=line.match(/^\s*(?:[-*•]|\d+[.、])\s+(.+)$/);
    return { name:'div',attrs:{style:heading?'font-weight:600;font-size:16px;margin:12px 0 6px;':bullet?'padding-left:12px;margin:5px 0;':'margin:4px 0;min-height:6px;'},children:inline(heading?heading[2]:bullet?'• '+bullet[1]:line) };
  });
}
module.exports={formatMessage};
