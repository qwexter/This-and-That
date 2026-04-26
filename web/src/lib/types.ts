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
	title: string;
}

export interface AddGroup {
	title: string;
}

export interface UpdateGroup {
	title: string;
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
