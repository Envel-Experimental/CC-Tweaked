# CC-Tweaked Modifications

This project uses a modified version of CC-Tweaked that includes the following feature enhancements:

## 1. Feature Parity for "Normal" Computers and Monitors
By default in ComputerCraft, "Normal" computers and monitors lack features like colored text, advanced mouse event interactions (like `mouse_drag` and `mouse_scroll`), and complex APIs, which are reserved for "Advanced" (gold-tier) variants. 

**Changes:**
- Normal Computers now instantiate with `colour = true` on their internal server terminals, instantly providing them the same color support and UI interaction capabilities as Advanced computers.
- Normal Monitors are now constructed using `ServerMonitor(true, this)` to ensure color features work seamlessly. 

*Note: These changes only exist on the server logic layer, meaning standard unmodified CC-Tweaked clients can connect and benefit from these features without requiring a client-side mod update.*

## 2. Computer Session Locking
To improve multiplayer behavior and prevent overlapping edits, computers maintain a "Player Session Lock".

**Changes:**
- **Locking:** When a player opens a computer terminal (Computer, Turtle, or Pocket Computer), they effectively "lock" it to their UUID for the duration their GUI is open.
- **Read-Only Mode:** If another player attempts to interact with the same computer while it is locked, they will be given read-only access (all input events such as typing, clicking, and file uploading are blocked).
- **Notification:** Read-only spectators will receive an ActionBar message reading `Busy: Read only` in red text.
- **Operator Override:** Players with a permission level of `2` or higher (Server Operators) bypass this lock entirely, and can take control or act simultaneously.
