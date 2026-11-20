import {createPlacePhotos, loadMaps, showAttributions} from './maps.js';
import {requestJson} from './api.js';

const main = document.querySelector('#main');
const form = document.querySelector('#search-form');
const status = document.querySelector('#status');
const error = document.querySelector('#search-error');
const results = document.querySelector('#results');
const listPanel = document.querySelector('#list-panel');
const mapPanel = document.querySelector('#map-panel');
const mapElement = document.querySelector('#map');
const mapStatus = document.querySelector('#map-status');
const searchArea = document.querySelector('#search-area');
const listTab = document.querySelector('#list-tab');
const mapTab = document.querySelector('#map-tab');
const desktop = window.matchMedia('(min-width: 768px)');

let maps, map, infoWindow, selectedLink;
let markers = [];
let markersDirty = true;
let pendingMap;
let requestVersion = 0;

function renderMarkers() {
    if (!map || mapPanel.hidden || !markersDirty) return;
    markersDirty = false;
    infoWindow.close();
    selectedLink = null;
    for (const marker of markers) marker.setMap(null);
    markers = [];

    const bounds = new maps.LatLngBounds();
    for (const row of results.querySelectorAll('li[data-lat][data-lon]')) {
        const position = {lat: Number(row.dataset.lat), lng: Number(row.dataset.lon)};
        const link = row.querySelector('a');
        const marker = new maps.Marker({map, position, title: link.textContent});
        const select = (withPhoto = false) => {
            selectedLink?.removeAttribute('aria-current');
            selectedLink = link;
            link.setAttribute('aria-current', 'true');
            const popup = document.createElement('div');
            const detailLink = document.createElement('a');
            detailLink.href = link.href;
            detailLink.textContent = link.textContent;
            popup.append(detailLink);
            infoWindow.setContent(popup);
            infoWindow.open({map, anchor: marker, shouldFocus: false});
            if (withPhoto) {
                createPlacePhotos(maps, row.dataset.googlePlaceId, {compact: true}).then(photo => {
                    if (photo && infoWindow.isOpen && infoWindow.getContent() === popup) popup.prepend(photo);
                });
            }
        };
        marker.addListener('click', () => select(true));
        link.addEventListener('focus', () => select());
        markers.push(marker);
        bounds.extend(position);
    }
    if (markers.length === 1) {
        map.setCenter(markers[0].getPosition());
        map.setZoom(15);
    } else if (markers.length > 1) {
        map.fitBounds(bounds);
    }
}

function ensureMap() {
    if (pendingMap) return;
    mapStatus.textContent = 'Loading map…';
    pendingMap = loadMaps(main.dataset.mapsKey).then(library => {
        maps = library;
        map = new maps.Map(mapElement, {center: {lat: 44.4268, lng: 26.1025}, zoom: 12});
        infoWindow = new maps.InfoWindow();
        searchArea.disabled = false;
        mapStatus.textContent = '';
        renderMarkers();
    }).catch(cause => {
        mapElement.hidden = true;
        searchArea.hidden = true;
        mapStatus.textContent = cause.message || 'The map is unavailable. Search results remain available in the list.';
    });
}

function updateView() {
    const focus = document.activeElement;
    const mapSelected = mapTab.getAttribute('aria-pressed') === 'true';
    listPanel.hidden = !desktop.matches && mapSelected;
    mapPanel.hidden = !desktop.matches && !mapSelected;
    if (listPanel.hidden && listPanel.contains(focus)) mapTab.focus();
    if (mapPanel.hidden && mapPanel.contains(focus)) listTab.focus();
    if (!mapPanel.hidden) {
        ensureMap();
        renderMarkers();
    }
}

function renderResult(place) {
    const row = document.createElement('li');
    if (place.googlePlaceId) row.dataset.googlePlaceId = place.googlePlaceId;
    if (place.latitude != null && place.longitude != null) {
        row.dataset.lat = place.latitude;
        row.dataset.lon = place.longitude;
    }
    if (place.category) {
        const label = document.createElement('p');
        label.className = 'eyebrow';
        label.textContent = place.category.replaceAll('_', ' ');
        row.append(label);
    }
    const link = document.createElement('a');
    link.href = place.id ? `${main.dataset.placesPath}/${place.id}`
        : `${main.dataset.placesPath}/google/${encodeURIComponent(place.googlePlaceId)}`;
    link.textContent = place.name;
    row.append(link);
    if (place.address) {
        const address = document.createElement('p');
        address.textContent = place.address;
        row.append(address);
    }
    const rating = document.createElement('p');
    rating.className = 'result-meta';
    rating.textContent = place.reviewCount ? `${place.averageRating.toFixed(1)} / 5 · ${place.reviewCount} community reviews`
        : 'No community reviews yet.';
    if (place.distanceMeters != null) rating.textContent += ` · ${(place.distanceMeters / 1000).toFixed(1)} km`;
    row.append(rating);
    return row;
}

function showError(message) {
    status.textContent = '';
    error.textContent = message;
    error.hidden = false;
}

async function load(url, onSuccess) {
    const version = ++requestVersion;
    status.textContent = 'Loading places…';
    error.hidden = true;
    results.setAttribute('aria-busy', 'true');
    try {
        const data = await requestJson(url);
        if (version !== requestVersion) return;
        results.replaceChildren(...data.items.map(renderResult));
        markersDirty = true;
        renderMarkers();
        showAttributions(document.querySelector('#attributions'), data.items);
        const count = data.items.length;
        status.textContent = count
            ? `${count} ${count === 1 ? 'place' : 'places'} found.${data.truncated ? ' Results are limited.' : ''}`
            : 'No places found. Try another search.';
        onSuccess?.();
    } catch (cause) {
        if (version === requestVersion) showError(cause.message || 'Places are unavailable.');
    } finally {
        if (version === requestVersion) results.removeAttribute('aria-busy');
    }
}

function filters(params) {
    for (const name of ['category', 'minRating']) {
        const value = form.elements[name].value;
        if (value) params.set(name, value);
    }
    if (form.elements.sort.value === 'rating') params.set('sort', 'rating');
    return params;
}

form.addEventListener('submit', event => {
    event.preventDefault();
    const params = filters(new URLSearchParams({q: form.elements.q.value}));
    load(`${main.dataset.searchApi}?${params}`, () => history.replaceState(null, '', `${form.action}?${params}`));
});

const nearby = document.querySelector('#nearby');
if (navigator.geolocation) {
    document.querySelector('#nearby-controls').hidden = false;
    nearby.addEventListener('click', () => {
        const version = ++requestVersion;
        results.removeAttribute('aria-busy');
        error.hidden = true;
        status.textContent = 'Finding your location…';
        navigator.geolocation.getCurrentPosition(position => {
            if (version !== requestVersion) return;
            const params = filters(new URLSearchParams({lat: position.coords.latitude, lon: position.coords.longitude}));
            load(`${main.dataset.nearbyApi}?${params}`);
        }, () => {
            if (version === requestVersion) showError('Could not find your location. Search by name instead.');
        }, {timeout: 10000});
    });
}

for (const tab of [listTab, mapTab]) {
    tab.addEventListener('click', () => {
        listTab.setAttribute('aria-pressed', String(tab === listTab));
        mapTab.setAttribute('aria-pressed', String(tab === mapTab));
        updateView();
    });
}
searchArea.addEventListener('click', () => {
    const center = map.getCenter();
    const params = filters(new URLSearchParams({lat: center.lat(), lon: center.lng()}));
    load(`${main.dataset.nearbyApi}?${params}`);
});
desktop.addEventListener('change', () => {
    const focus = document.activeElement;
    if (desktop.matches && (focus === listTab || focus === mapTab)) form.elements.q.focus();
    updateView();
});
main.classList.add('has-map-controls');
updateView();
