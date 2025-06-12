CREATE TABLE IF NOT EXISTS POSITION (
        "MMSI"        INTEGER NOT NULL PRIMARY KEY,
        "name"        VARCHAR(255) NOT NULL,
        "lon"         DOUBLE PRECISION NOT NULL,
        "lat"         DOUBLE PRECISION NOT NULL,
        "location"    GEOMETRY(POINT, 4326) NOT NULL,
        "cog"         DOUBLE PRECISION NOT NULL,
        "trueHeading" DOUBLE PRECISION NOT NULL,
        "sog"         DOUBLE PRECISION NOT NULL,
        "timestamp"   TIMESTAMP NOT NULL
);

CREATE INDEX "idx_position_location" ON public.POSITION USING GIST ("location");
CREATE INDEX "idx_position_timestamp" ON public.POSITION ("timestamp");
