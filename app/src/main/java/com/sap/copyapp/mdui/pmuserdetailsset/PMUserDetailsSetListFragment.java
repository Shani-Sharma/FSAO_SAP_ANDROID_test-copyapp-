package com.sap.copyapp.mdui.pmuserdetailsset;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Toast;
import com.sap.copyapp.R;
import com.sap.copyapp.app.SAPWizardApplication;
import com.sap.copyapp.mdui.BundleKeys;
import com.sap.copyapp.mdui.EntitySetListActivity;
import com.sap.copyapp.mdui.EntitySetListActivity.EntitySetName;
import com.sap.copyapp.mdui.InterfacedFragment;
import com.sap.copyapp.mdui.UIConstants;
import com.sap.copyapp.mdui.EntityKeyUtil;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.sap.copyapp.service.SAPServiceManager;
import com.sap.copyapp.repository.OperationResult;
import com.sap.copyapp.viewmodel.EntityViewModelFactory;
import com.sap.copyapp.viewmodel.pmuserdetails.PMUserDetailsViewModel;
import com.sap.cloud.android.odata.eam_ntf_create_entities.EAM_NTF_CREATE_EntitiesMetadata.EntitySets;
import com.sap.cloud.android.odata.eam_ntf_create_entities.PMUserDetails;
import com.sap.cloud.mobile.fiori.object.ObjectCell;
import com.sap.cloud.mobile.odata.DataValue;
import com.sap.cloud.mobile.odata.EntityValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class PMUserDetailsSetListFragment extends InterfacedFragment<PMUserDetails> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PMUserDetailsSetActivity.class);

    /**
     * Service manager to provide root URL of OData Service for Glide to load images if there are media resources
     * associated with the entity type
     */
    private SAPServiceManager sapServiceManager;

    /**
     * List adapter to be used with RecyclerView containing all instances of pMUserDetailsSet
     */
    private PMUserDetailsListAdapter adapter;

    private SwipeRefreshLayout refreshLayout;

    /**
     * View model of the entity type
     */
    private PMUserDetailsViewModel viewModel;

    private ActionMode actionMode;
    private Boolean isInActionMode = false;
    private List<Integer> selectedItems = new ArrayList<>();

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                refreshLayout.setRefreshing(true);
                refreshListData();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityTitle = getString(EntitySetListActivity.EntitySetName.PMUserDetailsSet.getTitleId());
        menu = R.menu.itemlist_menu;
        setHasOptionsMenu(true);
        if( savedInstanceState != null ) {
            isInActionMode = savedInstanceState.getBoolean("ActionMode");
        }

        sapServiceManager = ((SAPWizardApplication) currentActivity.getApplication()).getSAPServiceManager();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View objectHeader = currentActivity.findViewById(R.id.objectHeader);
        if( objectHeader != null) {
            objectHeader.setVisibility(View.GONE);
        }
        return inflater.inflate(R.layout.fragment_entityitem_list, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(this.menu, menu);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        currentActivity.setTitle(activityTitle);
        RecyclerView recyclerView = currentActivity.findViewById(R.id.item_list);
        if (recyclerView == null) throw new AssertionError();
        this.adapter = new PMUserDetailsListAdapter(currentActivity);
        recyclerView.setAdapter(adapter);

        setupRefreshLayout();
        refreshLayout.setRefreshing(true);

        navigationPropertyName = currentActivity.getIntent().getStringExtra("navigation");
        parentEntityData = currentActivity.getIntent().getParcelableExtra("parent");

        FloatingActionButton floatButton = currentActivity.findViewById(R.id.fab);
        if (floatButton != null) {
            if (navigationPropertyName != null && parentEntityData != null) {
                floatButton.hide();
            } else {
                floatButton.setOnClickListener((v) -> {
                    listener.onFragmentStateChange(UIConstants.EVENT_CREATE_NEW_ITEM, null);
                });
            }
        }

        sapServiceManager.openODataStore(() -> {
            prepareViewModel();
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("ActionMode", isInActionMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshListData();
    }

    /** Initializes the view model and add observers on it */
    private void prepareViewModel() {
        if( navigationPropertyName != null && parentEntityData != null ) {
            viewModel = new ViewModelProvider(currentActivity, new EntityViewModelFactory(currentActivity.getApplication(), navigationPropertyName, parentEntityData))
                .get(PMUserDetailsViewModel.class);
        } else {
            viewModel = new ViewModelProvider(currentActivity).get(PMUserDetailsViewModel.class);
            viewModel.initialRead(this::showError);
        }

        viewModel.getObservableItems().observe(getViewLifecycleOwner(), pMUserDetailsSet -> {
            if (pMUserDetailsSet != null) {
                adapter.setItems(pMUserDetailsSet);

                PMUserDetails item = containsItem(pMUserDetailsSet, viewModel.getSelectedEntity().getValue());
                if (item == null) {
                    item = pMUserDetailsSet.isEmpty() ? null : pMUserDetailsSet.get(0);
                }

                if (item == null) {
                    hideDetailFragment();
                } else {
                    viewModel.setInFocusId(adapter.getItemIdForPMUserDetails(item));
                    if (currentActivity.getResources().getBoolean(R.bool.two_pane)) {
                        viewModel.setSelectedEntity(item);
                        if (!isInActionMode && !((PMUserDetailsSetActivity) currentActivity).isNavigationDisabled) {
                            listener.onFragmentStateChange(UIConstants.EVENT_ITEM_CLICKED, item);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }
            }
            refreshLayout.setRefreshing(false);
        });

        viewModel.getReadResult().observe(getViewLifecycleOwner(), state -> {
            if (refreshLayout.isRefreshing()) {
                refreshLayout.setRefreshing(false);
            }
        });

        viewModel.getDeleteResult().observe(getViewLifecycleOwner(), result -> {
            onDeleteComplete(result);
        });
    }

    /** Searches 'item' in the refreshed list, if found, returns the one in list */
    private PMUserDetails containsItem(List<PMUserDetails> items, PMUserDetails item) {
        PMUserDetails found = null;
        if( item != null ) {
            for( PMUserDetails entity: items ) {
                if( adapter.getItemIdForPMUserDetails(entity) == adapter.getItemIdForPMUserDetails(item)) {
                    found = entity;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Hides the detail fragment.
     */
    private void hideDetailFragment() {
        Fragment detailFragment = currentActivity.getSupportFragmentManager().findFragmentByTag(UIConstants.DETAIL_FRAGMENT_TAG);
        if( detailFragment != null ) {
            currentActivity.getSupportFragmentManager().beginTransaction()
                .remove(detailFragment).commit();
        }
        if( secondaryToolbar != null ) {
            secondaryToolbar.getMenu().clear();
            secondaryToolbar.setTitle("");
        }
        View objectHeader = currentActivity.findViewById(R.id.objectHeader);
        if( objectHeader != null) {
            objectHeader.setVisibility(View.GONE);
        }
    }

    /** Callback function for delete operation */
    private void onDeleteComplete(@NonNull OperationResult<PMUserDetails> result) {
        if( progressBar != null ) {
            progressBar.setVisibility(View.INVISIBLE);
        }
        viewModel.removeAllSelected();
        if (actionMode != null) {
            actionMode.finish();
            isInActionMode = false;
        }

        if (result.getError() != null) {
            handleDeleteError();
        } else {
            refreshListData();
        }
    }

    /** Refreshes the list data */
    void refreshListData() {
        if (navigationPropertyName != null && parentEntityData != null) {
            viewModel.refresh((EntityValue) parentEntityData, navigationPropertyName);
        } else {
            viewModel.refresh();
        }
        adapter.notifyDataSetChanged();
    }

    /** Sets the selected item id into view model */
    private PMUserDetails setItemIdSelected(int itemId) {
        LiveData<List<PMUserDetails>> liveData = viewModel.getObservableItems();
        List<PMUserDetails> pMUserDetailsSet = liveData.getValue();
        if (pMUserDetailsSet != null && pMUserDetailsSet.size() > 0) {
            viewModel.setInFocusId(adapter.getItemIdForPMUserDetails(pMUserDetailsSet.get(itemId)));
            return pMUserDetailsSet.get(itemId);
        }
        return null;
    }

    /** Sets up the refresh layout */
    private void setupRefreshLayout() {
        refreshLayout = currentActivity.findViewById(R.id.swiperefresh);
        refreshLayout.setColorSchemeColors(UIConstants.FIORI_STANDARD_THEME_GLOBAL_DARK_BASE);
        refreshLayout.setProgressBackgroundColorSchemeColor(UIConstants.FIORI_STANDARD_THEME_BACKGROUND);
        refreshLayout.setOnRefreshListener(this::refreshListData);
    }

    /** Callback function to handle deletion error */
    private void handleDeleteError() {
        showError(getResources().getString(R.string.delete_failed_detail));
        refreshLayout.setRefreshing(false);
    }

    /**
     * Represents the action mode of the list.
     */
    public class PMUserDetailsSetListActionMode implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            isInActionMode = true;
            FloatingActionButton fab = currentActivity.findViewById(R.id.fab);
            if (fab != null) {
                fab.hide();
            }
            MenuInflater inflater = actionMode.getMenuInflater();
            inflater.inflate(R.menu.itemlist_view_options, menu);
            hideDetailFragment();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {
                case R.id.update_item:
                    PMUserDetails pMUserDetailsEntity = viewModel.getSelected(0);
                    if (viewModel.numberOfSelected() == 1 && pMUserDetailsEntity != null) {
                        isInActionMode = false;
                        actionMode.finish();
                        viewModel.setSelectedEntity(pMUserDetailsEntity);
                        if( currentActivity.getResources().getBoolean(R.bool.two_pane)) {
                            listener.onFragmentStateChange(UIConstants.EVENT_ITEM_CLICKED, pMUserDetailsEntity);
                        }
                        listener.onFragmentStateChange(UIConstants.EVENT_EDIT_ITEM, pMUserDetailsEntity);
                    }
                    return true;
                case R.id.delete_item:
                    listener.onFragmentStateChange(UIConstants.EVENT_ASK_DELETE_CONFIRMATION, null);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            isInActionMode = false;
            if (!(navigationPropertyName != null && parentEntityData != null)) {
                FloatingActionButton fab = currentActivity.findViewById(R.id.fab);
                if (fab != null) {
                    fab.show();
                }
            }
            selectedItems.clear();
            viewModel.removeAllSelected();
            refreshListData();
        }
    }

    /**
     * List adapter to be used with RecyclerView. It contains the set of pMUserDetailsSet.
     */
    public class PMUserDetailsListAdapter extends RecyclerView.Adapter<PMUserDetailsListAdapter.ViewHolder> {

        private Context context;

        /** Entire list of PMUserDetails collection */
        private List<PMUserDetails> pMUserDetailsSet;

        /** RecyclerView this adapter is associate with */
        private RecyclerView recyclerView;

        /** Flag to indicate whether we have checked retained selected pMUserDetailsSet */
        private boolean checkForSelectedOnCreate = false;

        public PMUserDetailsListAdapter(Context context) {
            this.context = context;
            this.recyclerView = currentActivity.findViewById(R.id.item_list);
            if (this.recyclerView == null) throw new AssertionError();
            setHasStableIds(true);
        }

        /**
         * Use DiffUtil to calculate the difference and dispatch them to the adapter
         * Note: Please use background thread for calculation if the list is large to avoid blocking main thread
         */
        @WorkerThread
        public void setItems(@NonNull List<PMUserDetails> currentPMUserDetailsSet) {
            if (pMUserDetailsSet == null) {
                pMUserDetailsSet = new ArrayList<>(currentPMUserDetailsSet);
                notifyItemRangeInserted(0, currentPMUserDetailsSet.size());
            } else {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return pMUserDetailsSet.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return currentPMUserDetailsSet.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return pMUserDetailsSet.get(oldItemPosition).getEntityKey().toString().equals(
                                currentPMUserDetailsSet.get(newItemPosition).getEntityKey().toString());
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        PMUserDetails pMUserDetailsEntity = pMUserDetailsSet.get(oldItemPosition);
                        return !pMUserDetailsEntity.isUpdated() && currentPMUserDetailsSet.get(newItemPosition).equals(pMUserDetailsEntity);
                    }

                    @Nullable
                    @Override
                    public Object getChangePayload(final int oldItemPosition, final int newItemPosition) {
                        return super.getChangePayload(oldItemPosition, newItemPosition);
                    }
                });
                pMUserDetailsSet.clear();
                pMUserDetailsSet.addAll(currentPMUserDetailsSet);
                result.dispatchUpdatesTo(this);
            }
        }

        @Override
        public final long getItemId(int position) {
            return getItemIdForPMUserDetails(pMUserDetailsSet.get(position));
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.element_entityitem_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {

            checkForRetainedSelection();

            final PMUserDetails pMUserDetailsEntity = pMUserDetailsSet.get(holder.getAdapterPosition());
            DataValue dataValue = pMUserDetailsEntity.getDataValue(PMUserDetails.department);
            if (dataValue != null) {
                holder.masterPropertyValue = dataValue.toString();
            }
            populateObjectCell(holder, pMUserDetailsEntity);

            boolean isActive = getItemIdForPMUserDetails(pMUserDetailsEntity) == viewModel.getInFocusId();
            if (isActive) {
                setItemIdSelected(holder.getAdapterPosition());
            }
            boolean isPMUserDetailsSelected = viewModel.selectedContains(pMUserDetailsEntity);
            setViewBackground(holder.objectCell, isPMUserDetailsSelected, isActive);

            holder.view.setOnLongClickListener(new onActionModeStartClickListener(holder));
            setOnClickListener(holder, pMUserDetailsEntity);

            setOnCheckedChangeListener(holder, pMUserDetailsEntity);
            holder.setSelected(isPMUserDetailsSelected);
            setDetailImage(holder, pMUserDetailsEntity);
        }

        /**
         * Check to see if there are an retained selected pMUserDetailsEntity on start.
         * This situation occurs when a rotation with selected pMUserDetailsSet is triggered by user.
         */
        private void checkForRetainedSelection() {
            if (!checkForSelectedOnCreate) {
                checkForSelectedOnCreate = true;
                if (viewModel.numberOfSelected() > 0) {
                    manageActionModeOnCheckedTransition();
                }
            }
        }

        /**
         * If there are selected pMUserDetailsSet via long press, clear them as click and long press are mutually exclusive
         * In addition, since we are clearing all selected pMUserDetailsSet via long press, finish the action mode.
         */
        private void resetSelected() {
            if (viewModel.numberOfSelected() > 0) {
                viewModel.removeAllSelected();
                if (actionMode != null) {
                    actionMode.finish();
                    actionMode = null;
                }
            }
        }

        /**
         * Attempt to locate previously clicked view and reset its background
         * Reset view model's inFocusId
         */
        private void resetPreviouslyClicked() {
            long inFocusId = viewModel.getInFocusId();
            ViewHolder viewHolder = (ViewHolder) recyclerView.findViewHolderForItemId(inFocusId);
            if (viewHolder != null) {
                setViewBackground(viewHolder.objectCell, viewHolder.isSelected, false);
            } else {
                viewModel.refresh();
            }
        }

        private void processClickAction(@NonNull ViewHolder viewHolder, @NonNull PMUserDetails pMUserDetailsEntity) {
            resetPreviouslyClicked();
            setViewBackground(viewHolder.objectCell, false, true);
            viewModel.setInFocusId(getItemIdForPMUserDetails(pMUserDetailsEntity));
        }

        /**
         * Set ViewHolder's view onClickListener
         *
         * @param holder
         * @param pMUserDetailsEntity associated with this ViewHolder
         */
        private void setOnClickListener(@NonNull ViewHolder holder, @NonNull PMUserDetails pMUserDetailsEntity) {
            holder.view.setOnClickListener(view -> {
                boolean isNavigationDisabled = ((PMUserDetailsSetActivity) currentActivity).isNavigationDisabled;
                if(isNavigationDisabled) {
                    Toast.makeText(currentActivity, "Please save your changes first...", Toast.LENGTH_LONG).show();
                } else {
                    resetSelected();
                    resetPreviouslyClicked();
                    processClickAction(holder, pMUserDetailsEntity);
                    viewModel.setSelectedEntity(pMUserDetailsEntity);
                    listener.onFragmentStateChange(UIConstants.EVENT_ITEM_CLICKED, pMUserDetailsEntity);
                }
            });
        }

        /**
         * Represents the listener to start the action mode
         */
        public class onActionModeStartClickListener implements View.OnClickListener, View.OnLongClickListener {

            ViewHolder holder;

            public onActionModeStartClickListener(@NonNull ViewHolder viewHolder) {
                this.holder = viewHolder;
            }

            @Override
            public void onClick(View view) {
                onAnyKindOfClick();
            }

            @Override
            public boolean onLongClick(View view) {
                return onAnyKindOfClick();
            }

            /** callback function for both normal and long click on an entity */
            private boolean onAnyKindOfClick() {
                boolean isNavigationDisabled = ((PMUserDetailsSetActivity) currentActivity).isNavigationDisabled;
                if( isNavigationDisabled ) {
                    Toast.makeText(currentActivity, "Please save your changes first...", Toast.LENGTH_LONG).show();
                } else {
                    if (!isInActionMode) {
                        actionMode = ((AppCompatActivity) currentActivity).startSupportActionMode(new PMUserDetailsSetListActionMode());
                        adapter.notifyDataSetChanged();
                    }
                    holder.setSelected(!holder.isSelected);
                }
                return true;
            }
        }

        /** sets the detail image to the given <code>viewHolder</code> */
        private void setDetailImage(@NonNull ViewHolder viewHolder, @NonNull PMUserDetails pMUserDetailsEntity) {
            if (isInActionMode) {
                int drawable;
                if (viewHolder.isSelected) {
                    drawable = R.drawable.ic_check_circle_black_24dp;
                } else {
                    drawable = R.drawable.ic_uncheck_circle_black_24dp;
                }
                viewHolder.objectCell.prepareDetailImageView().setScaleType(ImageView.ScaleType.FIT_CENTER);
                Glide.with(context)
                        .load(getResources().getDrawable(drawable, null))
                        .apply(new RequestOptions().fitCenter())
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(viewHolder.objectCell.prepareDetailImageView());
            } else if (viewHolder.masterPropertyValue != null && !viewHolder.masterPropertyValue.isEmpty()) {
                viewHolder.objectCell.setDetailImageCharacter(viewHolder.masterPropertyValue.substring(0, 1));
            } else {
                viewHolder.objectCell.setDetailImageCharacter("?");
            }
        }

        /**
         * Set ViewHolder's CheckBox onCheckedChangeListener
         *
         * @param holder
         * @param pMUserDetailsEntity associated with this ViewHolder
         */
        private void setOnCheckedChangeListener(@NonNull ViewHolder holder, @NonNull PMUserDetails pMUserDetailsEntity) {
            holder.checkBox.setOnCheckedChangeListener((compoundButton, checked) -> {
                if (checked) {
                    viewModel.addSelected(pMUserDetailsEntity);
                    manageActionModeOnCheckedTransition();
                    resetPreviouslyClicked();
                } else {
                    viewModel.removeSelected(pMUserDetailsEntity);
                    manageActionModeOnUncheckedTransition();
                }
                setViewBackground(holder.objectCell, viewModel.selectedContains(pMUserDetailsEntity), false);
                setDetailImage(holder, pMUserDetailsEntity);
            });
        }

        /*
         * Start Action Mode if it has not been started
         * This is only called when long press action results in a selection. Hence action mode may
         * not have been started. Along with starting action mode, title will be set.
         * If this is an additional selection, adjust title appropriately.
         */
        private void manageActionModeOnCheckedTransition() {
            if (actionMode == null) {
                actionMode = ((AppCompatActivity) currentActivity).startSupportActionMode(new PMUserDetailsSetListActionMode());
            }
            if (viewModel.numberOfSelected() > 1) {
                actionMode.getMenu().findItem(R.id.update_item).setVisible(false);
            }
            actionMode.setTitle(String.valueOf(viewModel.numberOfSelected()));
        }

        /*
         * This is called when one of the selected pMUserDetailsSet has been de-selected
         * On this event, we will determine if update action needs to be made visible or
         * action mode should be terminated (no more selected)
         */
        private void manageActionModeOnUncheckedTransition() {
            switch (viewModel.numberOfSelected()) {
                case 1:
                    actionMode.getMenu().findItem(R.id.update_item).setVisible(true);
                    break;

                case 0:
                    if (actionMode != null) {
                        actionMode.finish();
                        actionMode = null;
                    }
                    return;

                default:
            }
            actionMode.setTitle(String.valueOf(viewModel.numberOfSelected()));
        }

        private void populateObjectCell(@NonNull ViewHolder viewHolder, @NonNull PMUserDetails pMUserDetailsEntity) {

            DataValue dataValue = pMUserDetailsEntity.getDataValue(PMUserDetails.department);
            String masterPropertyValue = null;
            if (dataValue != null) {
                masterPropertyValue = dataValue.toString();
            }
            viewHolder.objectCell.setHeadline(masterPropertyValue);
            viewHolder.objectCell.setDetailImage(null);
            setDetailImage(viewHolder, pMUserDetailsEntity);

            viewHolder.objectCell.setSubheadline("Subheadline goes here");
            viewHolder.objectCell.setFootnote("Footnote goes here");
            if (masterPropertyValue == null || masterPropertyValue.isEmpty()) {
                viewHolder.objectCell.setIcon("?", 0);
            } else {
                viewHolder.objectCell.setIcon(masterPropertyValue.substring(0, 1), 0);
            }
            viewHolder.objectCell.setIcon(R.drawable.default_dot, 1, R.string.attachment_item_content_desc);
            viewHolder.objectCell.setIcon("!", 2);
        }

        /**
         * Set background of view to indicate pMUserDetailsEntity selection status
         * Selected and Active are mutually exclusive. Only one can be true
         *
         * @param view
         * @param isPMUserDetailsSelected - true if pMUserDetailsEntity is selected via long press action
         * @param isActive               - true if pMUserDetailsEntity is selected via click action
         */
        private void setViewBackground(@NonNull View view, boolean isPMUserDetailsSelected, boolean isActive) {
            boolean isMasterDetailView = currentActivity.getResources().getBoolean(R.bool.two_pane);
            if (isPMUserDetailsSelected) {
                view.setBackground(ContextCompat.getDrawable(context, R.drawable.list_item_selected));
            } else if (isActive && isMasterDetailView && !isInActionMode) {
                view.setBackground(ContextCompat.getDrawable(context, R.drawable.list_item_active));
            } else {
                view.setBackground(ContextCompat.getDrawable(context, R.drawable.list_item_default));
            }
        }

        @Override
        public int getItemCount() {
            if (pMUserDetailsSet == null) {
                return 0;
            } else {
                return pMUserDetailsSet.size();
            }
        }

        /**
         * Computes a stable ID for each PMUserDetails object for use to locate the ViewHolder
         *
         * @param pMUserDetailsEntity
         * @return an ID based on the primary key of PMUserDetails
         */
        private long getItemIdForPMUserDetails(PMUserDetails pMUserDetailsEntity) {
            return pMUserDetailsEntity.getEntityKey().toString().hashCode();
        }

        /**
         * ViewHolder for RecyclerView.
         * Each view has a Fiori ObjectCell and a checkbox (used by long press)
         */
        public class ViewHolder extends RecyclerView.ViewHolder {

            public final View view;

            public boolean isSelected;

            public String masterPropertyValue;

            /** Fiori ObjectCell to display pMUserDetailsEntity in list */
            public final ObjectCell objectCell;

            /** Checkbox for long press selection */
            public final CheckBox checkBox;

            public ViewHolder(View view) {
                super(view);
                this.view = view;
                objectCell = view.findViewById(R.id.content);
                checkBox = view.findViewById(R.id.cbx);
                isSelected = false;
            }

            public void setSelected(Boolean selected) {
                isSelected = selected;
                checkBox.setChecked(selected);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + objectCell.getDescription() + "'";
            }
        }
    }
}
