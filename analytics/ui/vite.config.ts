import { defineConfig } from "vite";
import solid from "vite-plugin-solid";
import path from "node:path";

export default defineConfig({
    base: "./",
    plugins: [solid()],
    server: {
        fs: {
            allow: [
                path.resolve(__dirname, "../.."),
                path.resolve(__dirname)
            ]
        }
    }
});

