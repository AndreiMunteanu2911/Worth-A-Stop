let loading;
export function loadMaps(key) {
  if (!key) return Promise.reject(new Error("The map is not configured. The place list remains available."));
  if (!loading) loading = new Promise((resolve,reject) => {
    const fail = message => { clearTimeout(timeout); reject(new Error(message)); };
    const timeout = setTimeout(() => fail("The map did not load. The place list remains available."), 12000);
    window.locationReviewMapsReady = () => { clearTimeout(timeout); resolve(window.google.maps); };
    window.gm_authFailure = () => fail("The map could not be authorized.");
    const script=document.createElement("script");
    script.src=`https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(key)}&loading=async&callback=locationReviewMapsReady&v=weekly`;
    script.async=true; script.onerror=()=>fail("The map could not be loaded."); document.head.append(script);
  });
  return loading;
}

export async function createPlacePhotos(maps, placeId, {compact = false} = {}) {
  if (!placeId) return null;
  try {
    const library = await maps.importLibrary("places");
    const details = compact ? new library.PlaceDetailsCompactElement() : new library.PlaceDetailsElement();
    const request = document.createElement("gmp-place-details-place-request");
    request.setAttribute("place", placeId);
    const media = document.createElement("gmp-place-media");
    if (compact) {
      details.style.cssText = "width: 200px; max-width: 100%; border: 0; color-scheme: light; --gmp-thumbnail-border-radius: 3px";
    } else {
      media.setAttribute("lightbox-preferred", "");
    }
    const attribution = document.createElement("gmp-place-attribution");
    details.append(request, media, attribution);
    details.addEventListener("gmp-error", () => details.remove(), {once: true});
    return details;
  } catch {
    return null;
  }
}

export function showAttributions(container, places) {
  container.replaceChildren();
  const label=document.createElement("span"); label.textContent="Google Maps"; container.append(label);
  const seen=new Set();
  for(const place of places) for(const attribution of place.attributions || []) {
    if(!attribution.provider || seen.has(attribution.provider)) continue;
    seen.add(attribution.provider); const item=document.createElement("span"); item.textContent=" · ";
    const link=document.createElement("a"); link.textContent=attribution.provider;
    try {const url=new URL(attribution.providerUri);if(url.protocol==="https:" || url.protocol==="http:")link.href=url.href;}catch{}
    item.append(link); container.append(item);
  }
}
