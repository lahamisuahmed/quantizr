package org.subnode.service;

import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.subnode.config.SessionContext;
import org.subnode.model.NodeInfo;
import org.subnode.model.client.NodeProp;
import org.subnode.mongo.MongoApi;
import org.subnode.mongo.MongoSession;
import org.subnode.mongo.RunAsMongoAdmin;
import org.subnode.mongo.model.SubNode;
import org.subnode.request.GetSharedNodesRequest;
import org.subnode.request.NodeFeedRequest;
import org.subnode.request.NodeSearchRequest;
import org.subnode.response.GetSharedNodesResponse;
import org.subnode.response.NodeFeedResponse;
import org.subnode.response.NodeSearchResponse;
import org.subnode.util.Convert;
import org.subnode.util.DateUtil;
import org.subnode.util.ThreadLocals;
import org.subnode.util.ValContainer;

/**
 * Service for searching the repository. This searching is currently very basic,
 * and just grabs the first 100 results. Despite it being basic right now, it is
 * however EXTREMELY high performance and leverages the full and best search
 * performance that can be gotten out of Lucene, which beats any other
 * technology in the world in it's power.
 * 
 * NOTE: the Query class DOES have a 'skip' and 'limit' which I can take
 * advantage of in all my searching but I'm not fully doing so yet I don't
 * believe.
 */
@Component
public class NodeSearchService {
	private static final Logger log = LoggerFactory.getLogger(NodeSearchService.class);
	private SimpleDateFormat dateFormat = new SimpleDateFormat(DateUtil.DATE_FORMAT_NO_TIMEZONE,
			DateUtil.DATE_FORMAT_LOCALE);

	@Autowired
	private MongoApi api;

	@Autowired
	private Convert convert;

	@Autowired
	private SessionContext sessionContext;

	@Autowired
	private RunAsMongoAdmin adminRunner;

	public NodeSearchResponse search(MongoSession session, NodeSearchRequest req) {
		NodeSearchResponse res = new NodeSearchResponse();
		if (session == null) {
			session = ThreadLocals.getMongoSession();
		}
		int MAX_NODES = 100;

		String searchText = req.getSearchText();

		List<NodeInfo> searchResults = new LinkedList<NodeInfo>();
		res.setSearchResults(searchResults);
		int counter = 0;

		if ("node.id".equals(req.getSearchProp())) {
			SubNode node = api.getNode(session, req.getSearchText(), true);
			if (node != null) {
				NodeInfo info = convert.convertToNodeInfo(sessionContext, session, node, true, true, false, counter + 1,
						false, false, false);
				searchResults.add(info);
			}
		}
		/*
		 * node.name is a special indicator meaning we're doing a node name lookup. Not
		 * a fuzzy search but an exact lookup.
		 */
		else if ("node.name".equals(req.getSearchProp())) {
			SubNode node = api.getNode(session, ":" + req.getSearchText(), true);
			if (node != null) {
				NodeInfo info = convert.convertToNodeInfo(sessionContext, session, node, true, true, false, counter + 1,
						false, false, false);
				searchResults.add(info);
			}
		}
		// othwerwise we're searching all node properties, only under the selected node.
		else {
			SubNode searchRoot = api.getNode(session, req.getNodeId());

			for (SubNode node : api.searchSubGraph(session, searchRoot, req.getSearchProp(), searchText,
					req.getSortField(), MAX_NODES, req.getFuzzy(), req.getCaseSensitive())) {
				NodeInfo info = convert.convertToNodeInfo(sessionContext, session, node, true, true, false, counter + 1,
						false, false, false);
				searchResults.add(info);
				if (counter++ > MAX_NODES) {
					break;
				}
			}
		}
		res.setSuccess(true);
		log.debug("search results count: " + counter);
		return res;
	}

	//todo-0: work in progress...feedNode not yet functional.
	public NodeFeedResponse nodeFeed(MongoSession session, NodeFeedRequest req) {
		NodeFeedResponse res = new NodeFeedResponse();
		if (session == null) {
			session = ThreadLocals.getMongoSession();
		}
		int MAX_NODES = 100;

		List<NodeInfo> searchResults = new LinkedList<NodeInfo>();
		res.setSearchResults(searchResults);
		int counter = 0;

		SubNode feedNode = api.getNode(session, req.getNodeId());
		if (feedNode != null) {

			// todo-0: update so that this can get only type==friend nodes.
			Iterable<SubNode> friendNodes = api.getChildrenUnderParentPath(session, feedNode.getPath(), null,
					MAX_NODES);

			for (SubNode friendNode : friendNodes) {
				String userName = null;
				String userNodeId = friendNode.getStringProp(NodeProp.USER_NODE_ID.s());

				/*
				 * when user first adds, this friendNode won't have the userNodeId yet to add if
				 * not existing
				 */
				if (userNodeId == null) {
					userName = friendNode.getStringProp(NodeProp.USER.s());

					ValContainer<SubNode> _userNode = new ValContainer<SubNode>();
					final String _userName = userName;
					adminRunner.run(s -> {
						_userNode.setVal(api.getUserNodeByUserName(s, _userName));
					});

					if (_userNode.getVal() != null) {
						userNodeId = _userNode.getVal().getId().toHexString();
						friendNode.setProp(NodeProp.USER_NODE_ID.s(), userNodeId);
					}
				}
				// String user = friendNode.getStringProp(NodeProp.USER.s());
				log.debug("Processing Friend Node[" + userName + "]: id=" + friendNode.getId().toHexString());
			}

			res.setSuccess(true);
			log.debug("search results count: " + counter);
		}
		return res;
	}

	public GetSharedNodesResponse getSharedNodes(MongoSession session, GetSharedNodesRequest req) {
		GetSharedNodesResponse res = new GetSharedNodesResponse();
		if (session == null) {
			session = ThreadLocals.getMongoSession();
		}
		int MAX_NODES = 100;

		List<NodeInfo> searchResults = new LinkedList<NodeInfo>();
		res.setSearchResults(searchResults);
		int counter = 0;

		SubNode searchRoot = api.getNode(session, req.getNodeId());

		for (SubNode node : api.searchSubGraphByAcl(session, searchRoot, SubNode.FIELD_MODIFY_TIME, MAX_NODES)) {

			/*
			 * If we're only looking for shares to a specific person (or public) then check
			 * here
			 */
			if (req.getShareTarget() != null && !node.getAc().containsKey(req.getShareTarget())) {
				continue;
			}

			NodeInfo info = convert.convertToNodeInfo(sessionContext, session, node, true, true, false, counter + 1,
					false, false, false);
			searchResults.add(info);
			if (counter++ > MAX_NODES) {
				break;
			}
		}

		res.setSuccess(true);
		log.debug("search results count: " + counter);
		return res;
	}
}
