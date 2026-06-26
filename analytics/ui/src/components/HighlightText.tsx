import { For } from "solid-js";

function escapeRegExp(str: string) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function HighlightText(props: { text?: string; query: string }) {
  if (!props.text) {
    return null;
  }
  if (!props.query.trim()) {
    return <span>{props.text}</span>;
  }
  try {
    const parts = props.text.split(new RegExp(`(${escapeRegExp(props.query.trim())})`, "gi"));
    return (
      <span>
        <For each={parts}>
          {(part) => 
            part.toLowerCase() === props.query.trim().toLowerCase() ? (
              <mark class="text-highlight">{part}</mark>
            ) : (
              part
            )
          }
        </For>
      </span>
    );
  } catch (e) {
    return <span>{props.text}</span>;
  }
}
