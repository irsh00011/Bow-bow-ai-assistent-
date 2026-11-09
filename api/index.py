"""Vercel serverless entry point for the BOW BOW FastAPI backend.

Vercel's Python runtime serves the ASGI app exported as `app` here.
vercel.json rewrites every path to this function, so the FastAPI app
handles all routes exactly as it does under uvicorn.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "backend"))

from main import app  # noqa: E402,F401  (FastAPI app; Vercel serves `app`)
