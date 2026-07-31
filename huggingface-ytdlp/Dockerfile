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

# Ensure python is mapped to python3 in path if needed
RUN ln -sf /usr/bin/python3 /usr/bin/python

# Set environment variables
ENV PORT=7860
EXPOSE 7860

# Run Express server
CMD ["node", "server.js"]
