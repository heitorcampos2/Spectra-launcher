package com.hazagou.spectra.remote

/** Controle pelo navegador do celular (quem escanear o QR com a câmera comum cai aqui). */
object RemoteWebPage {
    fun html(tvName: String): String {
        val name = tvName.replace("<", "").replace(">", "").replace("&", "e")
        return """
<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#120F33"><title>Controle — $name</title>
<style>
:root{--bg:#120F33;--card:#1E1A4E;--acc:#8B5CF6;--txt:#fff;--mut:#A9A3D9}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;user-select:none}
body{margin:0;background:radial-gradient(circle at 50% 0,#2A1F6B,var(--bg) 60%);color:var(--txt);font-family:system-ui,sans-serif;min-height:100vh}
main{max-width:420px;margin:0 auto;padding:18px 16px 40px}
h1{font-size:20px;margin:4px 0 2px}.sub{color:var(--mut);font-size:13px;margin-bottom:18px}
.pad{position:relative;width:260px;height:260px;margin:10px auto 18px;border-radius:50%;background:var(--card);box-shadow:0 10px 40px #0008}
.pad button{position:absolute;width:76px;height:76px;border-radius:50%;background:transparent;font-size:26px}
.u{top:4px;left:92px}.d{bottom:4px;left:92px}.l{left:4px;top:92px}.r{right:4px;top:92px}
.pad .ok{top:75px;left:75px;width:110px;height:110px;background:var(--acc);font-size:20px;font-weight:700}
button{border:0;color:var(--txt);background:var(--card);border-radius:18px;font-size:18px;padding:14px 0;cursor:pointer}
button:active{transform:scale(.94);filter:brightness(1.3)}
.row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-bottom:10px}
.row4{grid-template-columns:repeat(4,1fr)}
form{display:flex;gap:8px;margin-top:8px}input{flex:1;border:0;border-radius:14px;padding:14px;background:var(--card);color:#fff;font-size:16px}
#msg{min-height:20px;color:#FCA5A5;font-size:13px;text-align:center;margin:8px 0}
.pin{display:none;text-align:center}.pin input{text-align:center;letter-spacing:8px;font-size:22px}
.hint{color:var(--mut);font-size:12px;text-align:center;margin-top:16px}
</style></head><body><main>
<h1>📺 $name</h1><div class="sub">Spectra Launcher • controle pelo navegador</div>
<div class="pin" id="pinBox"><p>Digite o PIN definido na TV</p><form id="pinForm"><input id="pin" inputmode="numeric" maxlength="8" autocomplete="off"><button style="padding:0 18px">OK</button></form></div>
<div id="ctl">
<div class="pad"><button class="u" data-k="up">▲</button><button class="l" data-k="left">◀</button><button class="ok" data-k="ok">OK</button><button class="r" data-k="right">▶</button><button class="d" data-k="down">▼</button></div>
<div class="row"><button data-k="back">↩ Voltar</button><button data-k="home">⌂ Início</button><button data-k="menu">☰ Menu</button></div>
<div class="row row4"><button data-k="rew">⏪</button><button data-k="play">⏯</button><button data-k="ff">⏩</button><button data-k="oklong">⋯</button></div>
<div class="row"><button data-k="voldown">🔉 −</button><button data-k="mute">🔇</button><button data-k="volup">🔊 +</button></div>
<form id="txt"><input id="t" placeholder="Digitar na TV…" autocomplete="off"><button style="padding:0 18px">Enviar</button></form>
</div>
<div id="msg"></div>
<div class="hint">Instale o Spectra no celular para ter a lista de apps e o controle completo.</div>
</main><script>
const q=new URLSearchParams(location.search);let tok=q.get('t')||localStorage.getItem('spectra_t')||'';
if(q.get('t')){localStorage.setItem('spectra_t',tok);history.replaceState(null,'','/');}
const msg=m=>{document.getElementById('msg').textContent=m||''};
function need(){document.getElementById('pinBox').style.display='block';document.getElementById('ctl').style.display='none'}
if(!tok)need();
async function call(p,b){try{const r=await fetch(p,{method:'POST',headers:{'X-Spectra-Token':tok,'Content-Type':'application/json'},body:JSON.stringify(b||{})});
 const j=await r.json().catch(()=>({}));if(r.status==401){localStorage.removeItem('spectra_t');tok='';need()}msg(j.ok===false||!r.ok?(j.message||j.error||'Erro'):'');}catch(e){msg('TV fora de alcance')}}
document.querySelectorAll('[data-k]').forEach(b=>b.addEventListener('click',()=>{navigator.vibrate&&navigator.vibrate(12);call('/api/key?k='+b.dataset.k)}));
document.getElementById('txt').onsubmit=e=>{e.preventDefault();const i=document.getElementById('t');call('/api/text',{text:i.value});i.value=''};
document.getElementById('pinForm').onsubmit=async e=>{e.preventDefault();const r=await fetch('/api/pair',{method:'POST',body:JSON.stringify({pin:document.getElementById('pin').value})});
 const j=await r.json().catch(()=>({}));if(j.token){tok=j.token;localStorage.setItem('spectra_t',tok);document.getElementById('pinBox').style.display='none';document.getElementById('ctl').style.display='block';msg('')}else msg(j.error||'PIN incorreto')};
</script></body></html>
""".trimIndent()
    }
}
