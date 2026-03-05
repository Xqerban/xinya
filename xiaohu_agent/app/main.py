from fastapi import FastAPI
from .routes import router

app = FastAPI(title="xiaohu-agent")
app.include_router(router)