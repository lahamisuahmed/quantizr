import * as J from "../JavaIntf";
import { Comp } from "../widget/base/Comp";
import { AppState } from "../AppState";
import { CompIntf } from "../widget/base/CompIntf";

/* This interface is how Type Plugins are handled */
export interface TypeHandlerIntf {
    getTypeName(): string;
    getName(): string;
    render(node: J.NodeInfo, rowStyling: boolean, state: AppState): Comp;
    getIconClass(): string;
    allowAction(action : string): boolean;
    allowPropertyEdit(typeName: string, state: AppState): boolean;
    getAllowUserSelect();
    getDomPreUpdateFunction(parent: CompIntf): void;
    getCustomProperties(): string[];
    ensureDefaultProperties(node: J.NodeInfo);
}