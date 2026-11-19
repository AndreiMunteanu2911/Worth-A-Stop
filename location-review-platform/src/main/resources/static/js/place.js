import {loadMaps,showAttributions} from "./maps.js";
const main=document.querySelector(".directory"),status=document.querySelector("#status"),article=document.querySelector("#place"),button=document.querySelector("#resolve");
let current,map;
async function render(place) {
  current=place; article.hidden=false;
  status.textContent=place.metadataUnavailable?"Place details are temporarily unavailable. Your community content is retained.":"";
  document.querySelector("#name").textContent=place.name; document.title=`${place.name} | Location Review Platform`;
  document.querySelector("#category").textContent=(place.category || "").replaceAll("_"," ");
  document.querySelector("#address").textContent=place.address || "";
  document.querySelector("#rating").textContent=place.reviewCount?`${place.averageRating.toFixed(1)} / 5 · ${place.reviewCount} community reviews`:"No community reviews yet.";
  const contact=document.querySelector("#contact"), hours=document.querySelector("#opening-hours");contact.replaceChildren();hours.replaceChildren();
  if(place.phone){const phone=document.createElement("span");phone.textContent=place.phone;contact.append(phone);}
  if(place.website){try{const url=new URL(place.website);if(url.protocol==="https:"||url.protocol==="http:"){const link=document.createElement("a");link.href=url.href;link.textContent="Website";contact.append(document.createTextNode(" · "),link);}}catch{}}
  contact.hidden=!contact.childNodes.length;
  for(const day of place.openingHours || []){const item=document.createElement("li");item.textContent=day;hours.append(item);}hours.hidden=!hours.childNodes.length;
  button.hidden=Boolean(place.id)||main.dataset.signedIn!=="true"||place.metadataUnavailable;
  showAttributions(document.querySelector("#attributions"),[place]);
  if(!place.googlePlaceId){const attribution=document.querySelector("#attributions");attribution.replaceChildren();const link=document.createElement("a");link.href="https://www.openstreetmap.org/copyright";link.textContent="Legacy location data © OpenStreetMap contributors";attribution.append(link);}
  if(!map && place.latitude!=null && place.longitude!=null) {
    try {const maps=await loadMaps(main.dataset.mapsKey);map=new maps.Map(document.querySelector("#map"),{center:{lat:Number(place.latitude),lng:Number(place.longitude)},zoom:15});new maps.Marker({map,position:map.getCenter(),title:place.name});}
    catch(error){document.querySelector("#map-status").textContent=error.message;}
  }
  if(place.id && place.googlePlaceId)loadRecommendations(place.id);
  if(place.id) { document.querySelector("#reviews-sort").onchange=()=>loadReviews(place.id,0); loadReviews(place.id,0); } else document.querySelector("#reviews-status").textContent="Save this place to write a review.";
}
async function read(){try{const response=await fetch(main.dataset.api,{cache:"no-store"});const data=await response.json();if(!response.ok)throw new Error(data.detail);await render(data);}catch(error){status.textContent=error.message||"Place details are unavailable.";}}
button.addEventListener("click",async()=>{
  button.disabled=true;status.textContent="Saving place…";
  try{const headers={"Content-Type":"application/json"};headers[document.querySelector('meta[name="csrf-header"]').content]=document.querySelector('meta[name="csrf-token"]').content;
    const response=await fetch("/api/v1/places/resolve",{method:"POST",headers,body:JSON.stringify({googlePlaceId:current.googlePlaceId})});const data=await response.json();if(!response.ok)throw new Error(data.detail);await render(data);status.textContent="Place saved.";
  }catch(error){status.textContent=error.message||"Could not save this place.";}finally{button.disabled=false;}
});
async function loadRecommendations(id){
  try{const response=await fetch(`/api/v1/places/${id}/recommendations`,{cache:"no-store"});if(!response.ok)throw new Error();const places=await response.json(),list=document.querySelector("#recommendations");list.replaceChildren();
    for(const place of places){const item=document.createElement("li"),link=document.createElement("a");link.href=`/places/${place.id}`;link.textContent=place.name;item.append(link);list.append(item);}
    document.querySelector("#recommendation-status").textContent=places.length?"Nearby reviewed places in the same category:":"No nearby reviewed places to recommend yet.";
    showAttributions(document.querySelector("#attributions"),[current,...places]);
  }catch{document.querySelector("#recommendation-status").textContent="Recommendations are currently unavailable.";}
}
read();

async function loadReviews(id,page) {
  const status=document.querySelector("#reviews-status"),list=document.querySelector("#review-list"),action=document.querySelector("#review-action");
  action.replaceChildren(); const write=document.createElement("a"); write.href=main.dataset.signedIn==="true"?`/reviews/new?placeId=${id}`:"/login"; write.textContent=main.dataset.signedIn==="true"?"Write a review":"Log in to write a review"; action.append(write);
  try {
    const response=await fetch(`/api/v1/places/${id}/reviews?page=${page}&sort=${encodeURIComponent(document.querySelector("#reviews-sort").value)}`,{cache:"no-store"}); if(!response.ok) throw new Error(); const data=await response.json(); list.replaceChildren();
    for(const review of data.items) {
      const article=document.createElement("article"), heading=document.createElement("p"), author=document.createElement("a"),body=document.createElement("p");
      author.href=`/users/${review.author.id}`; author.textContent=review.author.displayName; heading.append(author,document.createTextNode(` · ${review.rating} / 5 · ${review.createdAt.slice(0,10)}${review.visitDate ? ` · Visited ${review.visitDate}` : ""}`)); body.textContent=review.body; article.append(heading,body);
      if(String(review.author.id)===main.dataset.viewerId) { const edit=document.createElement("a"); edit.href=`/reviews/${review.id}/edit`; edit.textContent="Edit review"; const form=document.createElement("form"); form.method="post"; form.action=`/reviews/${review.id}/delete`; const token=document.createElement("input"); token.type="hidden"; token.name="_csrf"; token.value=document.querySelector('meta[name="csrf-token"]').content; const remove=document.createElement("button"); remove.type="submit"; remove.textContent="Delete review"; form.append(token,remove); form.addEventListener("submit",event=>{if(!confirm("Delete your review?"))event.preventDefault();}); article.append(edit,form); }
      list.append(article);
    }
    status.textContent=data.items.length?"":"No reviews yet."; const previous=document.querySelector("#reviews-prev"),next=document.querySelector("#reviews-next"); previous.hidden=page===0; next.hidden=page+1>=data.totalPages; previous.onclick=()=>loadReviews(id,page-1); next.onclick=()=>loadReviews(id,page+1); document.querySelector("#reviews-page").textContent=data.totalPages?`Page ${page+1} of ${data.totalPages}`:"";
  } catch { status.textContent="Reviews are currently unavailable."; }
}
