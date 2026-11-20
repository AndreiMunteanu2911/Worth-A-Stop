import {loadMaps,showAttributions} from "./maps.js";
import {requestJson} from "./api.js";
const main=document.querySelector(".directory"),status=document.querySelector("#status"),article=document.querySelector("#place");
const bookmarkButton = document.querySelector("#bookmark");
const bookmarkStatus = document.querySelector("#bookmark-status");
const signedIn = main.dataset.signedIn === "true";
let bookmarked = main.dataset.bookmarked === "true";
let current, map;
let reviewRequest = 0, recommendationRequest = 0;
const placeActions = [
  {button: document.querySelector("#write-review"), link: document.querySelector("#review-action"),
    label: "Write a review", guestLabel: "Log in to write a review", url: id => `/reviews/new?placeId=${id}`},
  {button: document.querySelector("#add-to-collection"), link: document.querySelector("#collection-action"),
    label: "Add to a collection", guestLabel: "Log in to collect this place", url: id => `/collections?scope=mine&placeId=${id}`}
];

function setPending(pending) {
  bookmarkButton.disabled = pending;
  for (const {button} of placeActions) button.disabled = pending;
}

function renderBookmark() {
  bookmarkButton.hidden = !signedIn || (!current.id && current.metadataUnavailable);
  bookmarkButton.textContent = bookmarked ? "Saved · Remove" : "Save place";
  bookmarkButton.classList.toggle("is-saved", bookmarked);
  document.querySelector("#bookmark-login").hidden = signedIn;
}

function resolvePlace() {
  return requestJson("/api/v1/places/resolve", {method: "POST", payload: {googlePlaceId: current.googlePlaceId}});
}

bookmarkButton.addEventListener("click", async () => {
  if (bookmarkButton.disabled) return;
  setPending(true);
  bookmarkStatus.textContent = bookmarked ? "Removing saved place…" : "Saving place…";
  try {
    if (bookmarked) {
      await requestJson(`/api/v1/bookmarks/${current.id}`, {method: "DELETE"});
      bookmarked = false;
    } else {
      if (!current.id) render(await resolvePlace());
      const result = await requestJson("/api/v1/bookmarks", {method: "POST", payload: {placeId: current.id}});
      bookmarked = result.saved;
    }
    renderBookmark();
    bookmarkStatus.textContent = bookmarked ? "Place saved." : "Place removed from your saved list.";
  } catch (error) {
    bookmarkStatus.textContent = error.message || "Could not update your saved places.";
  } finally {
    setPending(false);
  }
});

function render(place) {
  current=place; article.hidden=false;
  renderBookmark();
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
  for (const action of placeActions) {
    action.button.hidden = !signedIn || Boolean(place.id) || place.metadataUnavailable;
    action.link.hidden = signedIn && !place.id;
    if (!action.link.hidden) action.link.href = signedIn ? action.url(place.id) : "/login";
    action.link.textContent = signedIn ? action.label : action.guestLabel;
  }
  showAttributions(document.querySelector("#attributions"),[place]);
  if(!place.googlePlaceId){const attribution=document.querySelector("#attributions");attribution.replaceChildren();const link=document.createElement("a");link.href="https://www.openstreetmap.org/copyright";link.textContent="Legacy location data © OpenStreetMap contributors";attribution.append(link);}
  if(!map && place.latitude!=null && place.longitude!=null) renderMap(place);
  const hasRecommendations = Boolean(place.id && place.googlePlaceId && !place.metadataUnavailable);
  document.querySelector("#more-places").hidden = !hasRecommendations;
  if (hasRecommendations) loadRecommendations(place.id);
  document.querySelector("#review-filter").hidden = !place.id;
  if (place.id) {
    document.querySelector("#reviews-sort").onchange = () => loadReviews(place.id, 0);
    loadReviews(place.id, 0);
  } else {
    document.querySelector("#reviews-status").textContent = "No community reviews yet.";
  }
}
async function renderMap(place) {
  const mapElement = document.querySelector("#map");
  const mapStatus = document.querySelector("#map-status");
  mapStatus.textContent = "Loading map…";
  try {
    const maps = await loadMaps(main.dataset.mapsKey);
    if (map) return;
    mapElement.hidden = false;
    map = new maps.Map(mapElement, {
      center: {lat: Number(place.latitude), lng: Number(place.longitude)}, zoom: 15
    });
    new maps.Marker({map, position: map.getCenter(), title: place.name});
    mapStatus.textContent = "";
  } catch (error) {
    mapElement.hidden = true;
    mapStatus.textContent = error.message;
  }
}
async function read() {
  status.textContent = "Loading place…";
  try {
    render(await requestJson(main.dataset.api));
  } catch (error) {
    status.textContent = error.message || "Place details are unavailable.";
  }
}
for (const action of placeActions) {
  action.button.addEventListener("click", async () => {
    if (action.button.disabled) return;
    setPending(true);
    status.textContent = "Verifying place…";
    try {
      const place = await resolvePlace();
      status.textContent = "";
      window.location.assign(action.url(place.id));
    } catch (error) {
      status.textContent = error.message || "Could not verify this place. Please try again.";
    } finally {
      setPending(false);
    }
  });
}
async function loadRecommendations(id){
  const version = ++recommendationRequest;
  document.querySelector("#recommendation-status").textContent = "Loading recommendations…";
  try {
    const places = await requestJson(`/api/v1/places/${id}/recommendations`);
    if (version !== recommendationRequest) return;
    const list = document.querySelector("#recommendations");
    list.replaceChildren();
    for(const place of places){const item=document.createElement("li"),link=document.createElement("a");link.href=`/places/${place.id}`;link.textContent=place.name;item.append(link);list.append(item);}
    document.querySelector("#recommendation-status").textContent=places.length?"Nearby reviewed places in the same category:":"No nearby reviewed places to recommend yet.";
    showAttributions(document.querySelector("#attributions"),[current,...places]);
  } catch {
    if (version === recommendationRequest) {
      document.querySelector("#recommendation-status").textContent = "Recommendations are currently unavailable.";
    }
  }
}
read();

async function loadReviews(id,page) {
  const status = document.querySelector("#reviews-status");
  const list = document.querySelector("#review-list");
  const version = ++reviewRequest;
  status.textContent = "Loading reviews…";
  list.setAttribute("aria-busy", "true");
  try {
    const sort = encodeURIComponent(document.querySelector("#reviews-sort").value);
    const data = await requestJson(`/api/v1/places/${id}/reviews?page=${page}&sort=${sort}`);
    if (version !== reviewRequest) return;
    list.replaceChildren();
    for(const review of data.items) {
      const article=document.createElement("article"), heading=document.createElement("p"), author=document.createElement("a"),body=document.createElement("p");
      author.href=`/users/${review.author.id}`; author.textContent=review.author.displayName; heading.append(author,document.createTextNode(` · ${review.rating} / 5 · ${review.createdAt.slice(0,10)}${review.visitDate ? ` · Visited ${review.visitDate}` : ""}`)); body.textContent=review.body; article.append(heading,body);
      article.classList.add("review-row");
      body.classList.add("review-copy");
      const reactions = document.createElement("div"), discussion = document.createElement("a");
      reactions.className = "review-actions";
      discussion.href = `/reviews/${review.id}`;
      discussion.textContent = "Discussion";
      const likeLabel = review.likeCount === 1 ? "1 like" : `${review.likeCount} likes`;
      reactions.append(document.createTextNode(review.likedByMe ? `${likeLabel} · Liked` : likeLabel), discussion);
      article.append(reactions);
      if (String(review.author.id) === main.dataset.viewerId) {
        const edit = document.createElement("a");
        edit.href = `/reviews/${review.id}/edit`;
        edit.textContent = "Edit review";
        const confirmation = document.createElement("details");
        confirmation.className = "delete-confirm";
        const summary = document.createElement("summary");
        summary.textContent = "Delete review";
        const form = document.createElement("form");
        form.method = "post";
        form.action = `/reviews/${review.id}/delete`;
        const token = document.createElement("input");
        token.type = "hidden";
        token.name = "_csrf";
        token.value = document.querySelector('meta[name="csrf-token"]').content;
        const remove = document.createElement("button");
        remove.type = "submit";
        remove.textContent = "Confirm deletion";
        form.append(token, remove);
        confirmation.append(summary, form);
        article.append(edit, confirmation);
      }
      list.append(article);
    }
    status.textContent = data.items.length ? "" : "No reviews yet.";
    const previous = document.querySelector("#reviews-prev");
    const next = document.querySelector("#reviews-next");
    const pageLabel = document.querySelector("#reviews-page");
    const focused = document.activeElement;
    const pagination = previous.closest("nav");
    previous.hidden = page === 0;
    next.hidden = page + 1 >= data.totalPages;
    previous.onclick = () => loadReviews(id, page - 1);
    next.onclick = () => loadReviews(id, page + 1);
    pageLabel.textContent = data.totalPages ? `Page ${page + 1} of ${data.totalPages}` : "";
    pagination.hidden = data.totalPages < 2;
    if ((focused === previous || focused === next) && (focused.hidden || pagination.hidden)) {
      (pagination.hidden ? document.querySelector("#reviews-sort") : pageLabel).focus();
    }
  } catch {
    if (version === reviewRequest) status.textContent = "Reviews are currently unavailable.";
  } finally {
    if (version === reviewRequest) list.removeAttribute("aria-busy");
  }
}
