import { ServerEventData } from '../interfaces/ServerEventData';
import { useEffect, RefObject, useState } from 'react';
import mapboxgl from 'mapbox-gl';
import { VesselData } from '../interfaces/VesselData';

// Define the expiration time for vessel markers, this could be passed by the user as well
const VESSEL_EXPIRATION_MS = 1 * 60 * 1000; // 2 minutes

function createMarker(): HTMLElement { 
  let marker = document.createElement('div');
  marker.className = 'vessel-marker';
  marker.style.backgroundImage = 'url(../../map-arrow.png)';
  marker.style.width = '32px';
  marker.style.height = '32px';
  marker.style.backgroundSize = '100%';
  return marker;
}

export function ShowVessels(
  wss: WebSocket | null, 
  map: RefObject<mapboxgl.Map | null>,
  zoomLimit: number,
) {
  const [vessels, setVessels] = useState<Map<string, VesselData>>(new Map());

  useEffect(() => {

    if(wss !== null) {
      wss.onmessage = (event) => {
        try {
          const data: ServerEventData = JSON.parse(event.data);
          // console.log('Received data:', data);
          // Ensure the map instance exists
          if (map.current && map.current.getZoom() >= zoomLimit) {
            setVessels(prevVessels => {

              const popup = new mapboxgl.Popup({ offset: 25 }).setText(
                `Vessel: ${data.name} (MMSI: ${data.MMSI})\n` +
                `Coordinates: ${data.lat.toFixed(4)}, ${data.lon.toFixed(4)}\n` +
                `COG: ${data.cog}° | SOG: ${data.sog} knots | Heading: ${data.trueHeading}°\n` +
                `Timestamp: ${new Date(data.timestamp).toLocaleString()}\n` +
                `Delay: ${(Date.now() - data.timestampReceived)} milliseconds`
              );
              
              let vessel: VesselData | undefined = prevVessels.get(data.MMSI);
              if (vessel !== undefined) {
                vessel.marker.remove();
                // Update the existing marker's position and rotation
                vessel.marker.setLngLat([data.lon, data.lat])
                  .setPopup(popup) // Update popup content
                  .setRotation(data.cog); // Update rotation based on COG
                
                // Re-add the marker to the map to ensure it updates visually
                vessel.marker.addTo(map.current!);
              } else {
                // Create a new marker for the vessel

                vessel = {
                  marker: new mapboxgl.Marker(createMarker(), {
                    rotation: data.cog
                  })
                  .setLngLat([data.lon, data.lat])
                  .setPopup(popup) // Add popup to the marker
                  .addTo(map.current!),
                  timestamp: Date.now() // Store the current timestamp
                };
              }
              // Store or update the marker in the hash map
              prevVessels.set(data.MMSI, vessel);
              
              return prevVessels;
            });
          }
        } catch (error) {
          console.error('Failed to parse message:', error);
        }
      };

      // Clean up the connection when the component unmounts
      return () => {
        //wss.close();
      };
    }
  }); // Empty dependency array ensures this effect runs only once

  useEffect(() => {
    const intervalId = setInterval(() => {
      const now = Date.now();
      let changed = false;

      for (const [mmsi, vesselData] of vessels.entries()) {
        if (now - vesselData.timestamp > VESSEL_EXPIRATION_MS) {
          vesselData.marker.remove();
          vessels.delete(mmsi);
          changed = true;
        }
      }

      if (changed) {
        setVessels(vessels);
      }
    }, 30000); // Check for expired vessels every 30 seconds

    return () => clearInterval(intervalId); // Cleanup interval on unmount
  }, [vessels]);

}