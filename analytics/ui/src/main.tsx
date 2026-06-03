import { render } from "solid-js/web";
import App from "./App";
import "./styles.css";

const root = document.getElementById("app");

if (!root) {
  throw new Error("Could not find #app root element.");
}

render(() => <App />, root);
