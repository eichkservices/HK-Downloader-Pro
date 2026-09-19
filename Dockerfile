FROM node:18-slim

# Install Python3, pip, ffmpeg, and curl
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install the latest yt-dlp package system-wide
RUN pip3 install --no-cache-dir --break-system-packages yt-dlp

WORKDIR /app

# Copy package files and install dependencies
COPY web/package*.json ./
RUN npm install --production

# Copy the rest of the web folder
COPY web/ .

# Map python path
RUN ln -sf /usr/bin/python3 /usr/bin/python

# Environment
ENV PORT=10000
EXPOSE 10000

CMD ["node", "server.js"]
