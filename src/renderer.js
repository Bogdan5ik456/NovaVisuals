const $ = s => document.querySelector(s);
const $$ = s => [...document.querySelectorAll(s)];
const status = $("#playStatus");
function message(text, type="info") { status.textContent=text; status.dataset.type=type; }
$("#playButton").addEventListener("click", () => message(`Обрано Minecraft ${$("#version").value} · ${$("#loader").value}. Запуск гри ще не підключено — це прототип.`, "warning"));
$$("[data-tab]").forEach(b => b.addEventListener("click", () => {
  $$("[data-tab]").forEach(x=>x.classList.toggle("active",x===b));
  $$(".tab-panel").forEach(p=>p.hidden=p.id!==`panel-${b.dataset.tab}`);
}));
const modal=$("#settingsModal");
function openSettings(){modal.classList.add("visible");modal.setAttribute("aria-hidden","false")}
function closeSettings(){modal.classList.remove("visible");modal.setAttribute("aria-hidden","true")}
$("#settingsButton").addEventListener("click",openSettings);
$("#closeSettings").addEventListener("click",closeSettings);
$("#settingsButton2").addEventListener("click",openSettings);
modal.addEventListener("click",e=>{if(e.target===modal)closeSettings()});
document.addEventListener("keydown",e=>{if(e.key==="Escape")closeSettings();if(e.code==="ShiftRight")openSettings()});
const controls=[["fpsToggle","fps"],["skyToggle","sky"],["crosshairToggle","crosshair"],["handScale","handScale"],["handX","handX"],["handY","handY"],["handZ","handZ"],["performance","performance"]];
function updateLabels(){for(const [id] of controls){const el=$("#"+id),out=$("#"+id+"Value");if(out)out.textContent=Number(el.value).toFixed(id==="handScale"?2:0)}}
controls.forEach(([id,key])=>{const el=$("#"+id),saved=localStorage.getItem("nova-"+key);if(saved!==null){if(el.type==="checkbox")el.checked=saved==="true";else el.value=saved}
el.addEventListener("input",()=>{localStorage.setItem("nova-"+key,el.type==="checkbox"?String(el.checked):el.value);updateLabels()})});
updateLabels();
$("#resetSettings").addEventListener("click",()=>{controls.forEach(([,k])=>localStorage.removeItem("nova-"+k));location.reload()});
$("#accountButton").addEventListener("click",()=>message("Microsoft-вхід та облікові записи сайту ще не підключені.","warning"));
$("#browseVisuals").addEventListener("click",()=>message("Каталог візуалів буде додано окремим етапом.","info"));
