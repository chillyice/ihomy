"""Serve the desk study beside a running local ihomy frontend (Python 3)."""

import argparse
from functools import partial
from http.client import HTTPConnection
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit


class DeskServer(ThreadingHTTPServer):
    # Vite can request many split chunks concurrently on a cold page load.
    request_queue_size = 128


class DeskPreview(SimpleHTTPRequestHandler):
    def serve(self, method):
        path = urlsplit(self.path).path
        if path.startswith("/__desk/"):
            self.path = self.path[len("/__desk"):]
            return super().do_GET() if method == "GET" else super().do_HEAD()
        if path == "/registerSW.js":
            # An installed app shell would otherwise intercept the study on reload.
            self.send_response(200)
            self.send_header("Content-Type", "application/javascript")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        connection = HTTPConnection("127.0.0.1", self.server.site_port, timeout=15)
        try:
            headers = {k: self.headers[k] for k in ("Accept", "Accept-Language", "Range") if k in self.headers}
            connection.request(method, self.path, headers=headers)
            response = connection.getresponse()
            self.send_response(response.status)
            for name in ("Content-Type", "Content-Length", "Content-Encoding", "Content-Range", "Cache-Control", "ETag", "Last-Modified", "Location"):
                if response.getheader(name):
                    self.send_header(name, response.getheader(name))
            self.end_headers()
            if method == "GET":
                self.copyfile(response, self.wfile)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            pass
        except OSError:
            self.send_error(502, "Start the local ihomy frontend or set --site-port.")
        finally:
            connection.close()

    def do_GET(self):
        self.serve("GET")

    def do_HEAD(self):
        self.serve("HEAD")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site-port", type=int, default=5173)
    parser.add_argument("--port", type=int, default=4197)
    args = parser.parse_args()
    if args.port == args.site_port:
        parser.error("Choose different ports for ihomy and the preview.")
    handler = partial(DeskPreview, directory=str(Path(__file__).resolve().parent))
    with DeskServer(("127.0.0.1", args.port), handler) as server:
        server.site_port = args.site_port
        print(f"http://127.0.0.1:{args.port}/__desk/", flush=True)
        server.serve_forever()
