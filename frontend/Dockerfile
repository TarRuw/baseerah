ARG BASEERAH_API_BASE_URL

FROM debian:stable-slim AS build-env
ARG BASEERAH_API_BASE_URL

RUN apt-get update && apt-get install -y curl git unzip xz-utils zip libglu1-mesa
RUN useradd -ms /bin/bash developer
USER developer
WORKDIR /home/developer

# Download stable Flutter SDK
RUN git clone https://github.com/flutter/flutter.git -b stable

# Set path
ENV PATH="/home/developer/flutter/bin:/home/developer/flutter/bin/cache/dart-sdk/bin:${PATH}"

# Run doctor and enable web
RUN flutter doctor
RUN flutter config --enable-web

# Copy project files
COPY --chown=developer:developer . .

# Build the app with the injected API URL backend variable
RUN flutter build web --release --dart-define=BASEERAH_API_BASE_URL=${BASEERAH_API_BASE_URL}

# Stage 2: Serve the app using Nginx
FROM nginx:alpine
COPY --from=build-env /home/developer/build/web /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]