### Luồng xử lý hành động Save as Draft cho standalone Canvas file

```mermaid
graph TD
    A([Click Save]) --> B{projectId exists?}
    
    %% Trường hợp đã có Project ID
    B -- Yes --> C[sheetService.updateSheet]
    C --> D[toast "Saved" ✓]
    
    %% Trường hợp chưa có Project ID
    B -- No --> E[setShowSaveDialog true]
    E --> F[Hiển thị Save Diagram Dialog]