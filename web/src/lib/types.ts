export type TaskStatus = 'Todo' | 'Done';
export type TaskPriority = 'Low' | 'Medium' | 'High';

export interface Task {
	id: string;
	groupId: string | null;
	name: string;
	description: string | null;
	status: TaskStatus;
	priority: TaskPriority;
	deadline: string | null;
}

export interface AddTask {
	name: string;
	description: string | null;
	status: TaskStatus | null;
	priority: TaskPriority;
	deadline: string | null;
	groupId?: string | null;
}

export interface UpdateTask {
	name?: string;
	description?: string | null;
	status?: TaskStatus;
	priority?: TaskPriority;
	deadline?: string | null;
	groupId?: string | null;
	clearGroup?: boolean;
}

export interface Record {
	id: string;
	groupId: string | null;
	title: string;
	content: string | null;
}

export interface AddRecord {
	title: string;
	content?: string | null;
	groupId?: string | null;
}

export interface UpdateRecord {
	title?: string;
	content?: string | null;
	groupId?: string | null;
	clearGroup?: boolean;
}

export interface Group {
	id: string;
	spaceId: string | null;
	title: string;
}

export interface AddGroup {
	title: string;
	spaceId?: string | null;
}

export interface UpdateGroup {
	title?: string;
	spaceId?: string | null;
	clearSpace?: boolean;
}

export interface Space {
	id: string;
	title: string;
	ownerId: string;
	isPrivate: boolean;
}

export interface AddSpace {
	title: string;
}

export interface UpdateSpace {
	title: string;
}

export interface SpaceMember {
	userId: string;
}

export interface AddSpaceMember {
	userId: string;
}

export type FeedTaskChild = {
	kind: 'task';
	id: string;
	name: string;
	description: string | null;
	status: TaskStatus;
	priority: TaskPriority;
	deadline: string | null;
	createdAt: string;
};

export type FeedRecordChild = {
	kind: 'record';
	id: string;
	title: string;
	content: string | null;
	createdAt: string;
};

export type FeedChild = FeedTaskChild | FeedRecordChild;

export type FeedGroupEntry = {
	kind: 'group';
	id: string;
	title: string;
	createdAt: string;
	children: FeedChild[];
};

export type FeedTaskEntry = {
	kind: 'task';
	id: string;
	groupId: string | null;
	name: string;
	description: string | null;
	status: TaskStatus;
	priority: TaskPriority;
	deadline: string | null;
	createdAt: string;
};

export type FeedRecordEntry = {
	kind: 'record';
	id: string;
	groupId: string | null;
	title: string;
	content: string | null;
	createdAt: string;
};

export type FeedEntry = FeedGroupEntry | FeedTaskEntry | FeedRecordEntry;

export interface FeedPage {
	items: FeedEntry[];
	total: number;
	offset: number;
	limit: number;
}

export type AddGroupItem =
	| { kind: 'newTask'; name: string; description?: string | null; priority?: TaskPriority; deadline?: string | null }
	| { kind: 'newRecord'; title: string; content?: string | null }
	| { kind: 'existingTask'; id: string }
	| { kind: 'existingRecord'; id: string };

export interface AddGroupItemsRequest {
	items: AddGroupItem[];
}

export type GroupItemResponse =
	| { kind: 'task'; id: string; groupId: string; name: string; description: string | null; status: TaskStatus; priority: TaskPriority; deadline: string | null }
	| { kind: 'record'; id: string; groupId: string; title: string; content: string | null };

export interface AddGroupItemsResponse {
	items: GroupItemResponse[];
}

export interface Me {
	userId: string;
	displayName: string;
}

 export interface SpaceInviteResponse {
	token: string;
}

export interface InviteInfoResponse {
	spaceTitle: string;
}
