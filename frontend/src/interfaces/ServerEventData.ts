
// --- Type Definitions ---

import { GeoJSONFeature } from "mapbox-gl";

// Defines the structure of the data we expect from the WebSocket server
export interface ServerEventData {
  MMSI: string; // Maritime Mobile Service Identity
  name: string; // Name of the vessel
  lon: number;
  lat: number;
  cog: number; // Course Over Ground
  trueHeading: number; // Heading in degrees
  sog: number; // Speed Over Ground
  timestamp: string; // ISO 8601 format
  timestampReceived: number; // Timestamp when the data was received in the backend
}
