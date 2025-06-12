import React, { useRef, useEffect, useState } from 'react';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';
import './App.css';
import { ShowVessels } from './hooks/showVessels';

const mapboxToken = process.env["REACT_APP_MAPBOX_TOKEN"];
const wsURL = process.env["REACT_APP_WEBSOCKET_URL"] || "ws://127.0.0.1:8088/getAISData";
mapboxgl.accessToken = mapboxToken;
const ZOOM_LEVEL_LIMIT = Number(process.env["REACT_APP_ZOOM_LIMIT"]) || 12;

function App() {
  const mapContainer = useRef<HTMLDivElement>(null);
  
  const map = useRef<mapboxgl.Map | null>(null);

  const [socket, setSocket] = useState<WebSocket | null>(null);
  const [age, setAge] = useState<number>(2);

  ShowVessels(socket, map, ZOOM_LEVEL_LIMIT);

  // Define types for the state variables
  const [lng, setLng] = useState<number>(2.990216);
  const [lat, setLat] = useState<number>(40.748648);
  const [zoom, setZoom] = useState<number>(9);

  const createWebSocket = () => {
    return new WebSocket(wsURL);
  }

  useEffect(() => {
    // Prevent the map from re-initializing on every render
    if (mapContainer.current) {
      map.current = new mapboxgl.Map({
        container: mapContainer.current,
        style: 'mapbox://styles/mapbox/streets-v11',
        center: [lng, lat],
        zoom: zoom
      });

      // Add zoom and scale controls
      map.current.addControl(new mapboxgl.NavigationControl(), 'top-left');
      map.current.addControl(new mapboxgl.ScaleControl(), 'bottom-right');
    }

    // Clean up on unmount
    return () => {
      console.log('Unmounting map.remove');
      map.current?.remove();
    };
  }, []); 

  useEffect(() => {
    // Initialize WebSocket connection
    /* eslint-disable-next-line no-restricted-globals */
    const wss = createWebSocket();

    // Event: Connection is successfully opened
    wss.onopen = (event) => {
        console.log('WebSocket connection opened:', event);
        setSocket(wss)
    };

    // Event: The connection is closed
    wss.onclose = (event) => {
      console.log('[App.tsx] WebSocket: Connection closed.', event);
      setSocket(null);
    };

    // Event: An error occurs
    wss.onerror = (error) => {
      console.error('[App.tsx] WebSocket: Error.', error);
      setSocket(null);
    };

    // Clean up the connection when the component unmounts or before the effect re-runs
    return () => {
      console.log('[App.tsx] WebSocket: Cleaning up. Closing WebSocket.');
      if (wss.readyState === WebSocket.OPEN || wss.readyState === WebSocket.CONNECTING) {
        wss.close();
      }
    };
  }, []);

  useEffect(() => {
    // Add event listener for map movement
      if (map.current) {
        if(socket && 
          socket.readyState === WebSocket.OPEN && 
          zoom >= Number(ZOOM_LEVEL_LIMIT)) {
          let bounds = map.current.getBounds();
          socket.send(JSON.stringify({
            lat1: bounds?.getNorthEast().lat,
            long1: bounds?.getNorthEast().lng,
            lat2: bounds?.getSouthWest().lat,
            long2: bounds?.getSouthWest().lng,
            age: age,
          }));
        }
      }
  }, [lng, lat, zoom, age]);

  useEffect(() => {
    const updateMapState = () => {
      if (map.current) {
          setLng(Number(map.current.getCenter().lng.toFixed(4)));
          setLat(Number(map.current.getCenter().lat.toFixed(4)));
          setZoom(Number(map.current.getZoom().toFixed(2)));
        }
    }

    map.current?.on('moveend', updateMapState);
    map.current?.on('zoomend', updateMapState);
  }, []);

  const handleSliderChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    //  Number((document.getElementById('slider') as HTMLInputElement).value);
    setAge(Number(event.target.value));
  };

  return (
    <div>
      <div className="sidebar">
        Longitude: {lng} | Latitude: {lat} | Zoom: {zoom}
      </div>
      <div className="map-overlay top">
        <div className="map-overlay-inner">
          <h2>Data age in minutes: {age}</h2>
          <label id="age"></label>
          <input id="slider" type="range" min="2" max="60" step="1" value={age} onChange={handleSliderChange}/>
        </div>
      </div>
      <div ref={mapContainer} className="map-container" />
    </div>
  );
}

export default App;
