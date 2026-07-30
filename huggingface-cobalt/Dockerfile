FROM ghcr.io/imputnet/cobalt:10

# Hugging Face Spaces routes incoming web traffic to port 7860
ENV PORT=7860
ENV CORS_WILDCARD=1

# Note: Replace YOUR_HF_USERNAME with your actual Hugging Face username
# Example: https://hassan-hk-cobalt-api.hf.space/
ENV API_URL="https://YOUR_HF_USERNAME-hk-cobalt-api.hf.space/"

EXPOSE 7860
