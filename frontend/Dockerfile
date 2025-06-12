# frontend/Dockerfile

# ---- Build Stage ----
# Use a Node.js image to build the static assets
FROM node:20-alpine
WORKDIR /app

# Copy package.json and install dependencies first to leverage Docker's layer caching
COPY package.json package-lock.json* ./
RUN npm install

# Copy the rest of the application source code
COPY ./src ./src
COPY ./public ./public
COPY ./*.json .
COPY .env .

# Build the production-ready static files
RUN npm run build
RUN npm install -g serve

EXPOSE 3000

ENTRYPOINT ["serve", "-s", "build"]

# ---- Serve Stage ----
# Use a lightweight Nginx image to serve the static files
#FROM nginx:stable-alpine
#WORKDIR /usr/share/nginx/html
#
## Remove the default Nginx welcome page
#RUN rm -rf ./*
#
## Copy the built assets from the 'build' stage
## The build output is typically in a 'build' or 'dist' folder. Adjust if yours is different.
#COPY --from=build /app/build .
#
## Expose port 80 for the Nginx web server
#EXPOSE 80
#
## The default Nginx command will start the server, so no ENTRYPOINT is needed.
## The container will automatically start Nginx and serve the files from /usr/share/nginx/html
