import { DialogBase } from "../DialogBase";
import * as I from "../Interfaces";
import * as J from "../JavaIntf";
import { ShareToPersonDlg } from "./ShareToPersonDlg";
import { ButtonBar } from "../widget/ButtonBar";
import { Button } from "../widget/Button";
import { EditPrivsTable } from "../widget/EditPrivsTable";
import { PubSub } from "../PubSub";
import { Constants as C } from "../Constants";
import { Singletons } from "../Singletons";
import { Form } from "../widget/Form";
import { AppState } from "../AppState";
import { CompIntf } from "../widget/base/CompIntf";

let S: Singletons;
PubSub.sub(C.PUBSUB_SingletonsReady, (ctx: Singletons) => {
    S = ctx;
});

export class SharingDlg extends DialogBase {
    privsTable: EditPrivsTable;
    nodePrivsInfo: I.NodePrivilegesInfo;
    dirty: boolean = false;

    constructor(private node: J.NodeInfo, state: AppState) {
        super("Node Sharing", "app-modal-content-medium-width", null, state);
    }

    renderDlg(): CompIntf[] {
        let children = [
            new Form(null, [
                this.privsTable = new EditPrivsTable(this.nodePrivsInfo, this.removePrivilege),
                new ButtonBar([
                    new Button("Share with Person", this.shareToPersonDlg, null, "btn-primary"),
                    new Button("Share to Public", this.shareNodeToPublic, null, "btn-primary"),
                    new Button("Close", () => {
                        this.close();
                        S.meta64.refresh(this.appState);
                    })
                ])
            ])
        ];
        this.reload();
        return children;
    }

    /*
     * Gets privileges from server and displays in GUI also. Assumes gui is already at correct page.
     */
    reload = (): void => {
        S.util.ajax<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            "nodeId": this.node.id,
            "includeAcl": true,
            "includeOwners": true
        }, this.populate);
    }

    /*
     * Processes the response gotten back from the server containing ACL info so we can populate the sharing page in the gui
     */
    populate = (res: J.GetNodePrivilegesResponse): void => {
        //console.log("populating with: res="+S.util.prettyPrint(res));

        //todo-0: this *should* fail until I make it 'mergeState' instead right?
        this.privsTable.setState(res); 
    }

    removePrivilege = (principalNodeId: string, privilege: string): void => {
        S.util.ajax<J.RemovePrivilegeRequest, J.RemovePrivilegeResponse>("removePrivilege", {
            "nodeId": this.node.id,
            "principalNodeId": principalNodeId,
            "privilege": privilege
        }, this.removePrivilegeResponse);
    }

    removePrivilegeResponse = (res: J.RemovePrivilegeResponse): void => {
        S.util.ajax<J.GetNodePrivilegesRequest, J.GetNodePrivilegesResponse>("getNodePrivileges", {
            "nodeId": this.node.id,
            "includeAcl": true,
            "includeOwners": true
        }, this.populate);
    }

    shareToPersonDlg = (): void => {
        let dlg = new ShareToPersonDlg(this.node, this.reload, this.appState);
        dlg.open();
    }

    shareNodeToPublic = (): void => {
        let encrypted = S.props.isEncrypted(this.node);
        if (encrypted) {
            S.util.showMessage("This node is encrypted, and therefore cannot be made public.", "Warning");
            return;
        }

        console.log("Sharing node to public.");

        /*
         * Add privilege and then reload share nodes dialog from scratch doing another callback to server
         *
         * TODO: this additional call can be avoided as an optimization
         */
        S.util.ajax<J.AddPrivilegeRequest, J.AddPrivilegeResponse>("addPrivilege", {
            "nodeId": this.node.id,
            "principal": "public",
            "privileges": [J.PrivilegeType.READ],
        }, this.reload);
    }
}
