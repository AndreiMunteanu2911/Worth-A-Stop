import {loadMaps,showAttributions} from "./maps.js";
const main=document.querySelector(".directory"), form=document.querySelector("#search-form"), status=document.querySelector("#status"), results=document.querySelector("#results");
let map, markers=[], requestVersion=0;
async function initMap() {
  if(map) return;
  try {
    const maps=await loadMaps(main.dataset.mapsKey);
    if(!map) map=new maps.Map(document.querySelector("#map"),{center:{lat:44.4268,lng:26.1025},zoom:12});
  } catch(error) { document.querySelector("#map-status").textContent=error.message; }
}
async function load(url) {
  const version=++requestVersion; status.textContent="Loading places…";
  try {
    const response=await fetch(url,{cache:"no-store"}); const data=await response.json();
    if(version!==requestVersion) return;
    if(!response.ok) throw new Error(data.detail || "Places are unavailable.");
    results.replaceChildren(); markers.forEach(marker=>marker.setMap(null)); markers=[];
    const bounds=map?new google.maps.LatLngBounds():null;
    for(const place of data.items) {
      const row=document.createElement("li"), label=document.createElement("p"), link=document.createElement("a"), address=document.createElement("p"), rating=document.createElement("p");
      label.className="eyebrow"; label.textContent=place.category.replaceAll("_"," ");
      link.textContent=place.name; link.href=place.id?`/places/${place.id}`:`/places/google/${encodeURIComponent(place.googlePlaceId)}`;
      address.textContent=place.address || "";
      rating.textContent=place.reviewCount?`${place.averageRating.toFixed(1)} / 5 · ${place.reviewCount} community reviews`:"No community reviews yet.";
      if(place.distanceMeters!=null) rating.textContent+=` · ${(place.distanceMeters/1000).toFixed(1)} km`;
      row.append(label,link,address,rating); results.append(row);
      if(map) {
        const marker=new google.maps.Marker({map,position:{lat:Number(place.latitude),lng:Number(place.longitude)},title:place.name});
        const popup=document.createElement("a"); popup.textContent=place.name; popup.href=link.href;
        const info=new google.maps.InfoWindow({content:popup});
        const select=()=>{results.querySelectorAll("li").forEach(item=>item.removeAttribute("aria-current"));row.setAttribute("aria-current","true"); info.open({map,anchor:marker});};
        marker.addListener("click",select); row.addEventListener("focusin",select); markers.push(marker); bounds.extend(marker.getPosition());
      }
    }
    if(map && data.items.length) { map.fitBounds(bounds); if(data.items.length===1) map.setZoom(15); }
    showAttributions(document.querySelector("#attributions"),data.items);
    status.textContent=data.items.length?`${data.items.length} places found${data.truncated?"; results are limited":""}.`:"No places found. Try another search.";
  } catch(error) { if(version===requestVersion) status.textContent=error.message || "Places are unavailable."; }
}
function filters(params) {
  for(const name of ["category","minRating"]) {const value=form.elements[name].value;if(value)params.set(name,value);}
  if(form.elements.sort.value==="rating")params.set("sort","rating"); return params;
}
form.addEventListener("submit",event=>{event.preventDefault();const params=filters(new URLSearchParams({q:form.elements.q.value}));load(`/api/v1/places/search?${params}`);});
document.querySelector("#nearby").addEventListener("click",()=>{
  if(!navigator.geolocation){status.textContent="Location is unavailable. Search by name instead.";return;}
  status.textContent="Finding your location…";
  navigator.geolocation.getCurrentPosition(pos=>load(`/api/v1/places/nearby?${filters(new URLSearchParams({lat:pos.coords.latitude,lon:pos.coords.longitude}))}`),()=>status.textContent="Location could not be obtained. Search by name instead.",{timeout:10000});
});
for(const name of ["list","map"])document.querySelector(`#${name}-tab`).addEventListener("click",()=>{
  main.classList.toggle("show-map",name==="map");
  for(const other of ["list","map"])document.querySelector(`#${other}-tab`).setAttribute("aria-pressed",String(other===name));
  if(name==="map")initMap();
});
document.querySelector("#search-area").addEventListener("click",()=>{
  if(!map){status.textContent="The map is unavailable. Search by name instead.";return;}
  const center=map.getCenter();load(`/api/v1/places/nearby?${filters(new URLSearchParams({lat:center.lat(),lon:center.lng()}))}`);
});
initMap();
