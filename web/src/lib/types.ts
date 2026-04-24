export type TaskStatus = 'Todo' | 'Done';
export type TaskPriority = 'Low' | 'Medium' | 'High';

export interface Task {
	id: string;
	name: string;
	description: string | null;
	status: TaskStatus;
	priority: TaskPriority;
	deadline: string | null;
}

export interface AddTask {
	name: string;
	description?: string | null;
	status?: TaskStatus;
	priority?: TaskPriority;
	deadline?: string | null;
}

export interface UpdateTask {
	name?: string;
	description?: string | null;
	status?: TaskStatus;
	priority?: TaskPriority;
	deadline?: string | null;
}
