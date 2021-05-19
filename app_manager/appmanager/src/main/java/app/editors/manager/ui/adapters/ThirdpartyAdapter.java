package app.editors.manager.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.recyclerview.widget.RecyclerView;

import app.documents.core.network.ApiContract;
import app.editors.manager.R;
import app.editors.manager.mvp.models.user.Thirdparty;
import butterknife.BindView;
import butterknife.ButterKnife;
import lib.toolkit.base.ui.adapters.BaseListAdapter;

public class ThirdpartyAdapter extends BaseListAdapter<Thirdparty> {

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.thirdparty_item_layout, parent, false);
        return new ThirdpartyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ThirdpartyViewHolder) {
            ((ThirdpartyViewHolder) holder).bind(mList.get(position));
        }
    }

    protected class ThirdpartyViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.imageItem)
        AppCompatImageView mImage;
        @BindView(R.id.titleItem)
        AppCompatTextView mTitle;

        ThirdpartyViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        public void bind(Thirdparty item) {
            mTitle.setText(item.getTitle());
            switch (item.getProviderKey()) {
                case ApiContract.Storage.BOXNET:
                    mImage.setImageResource(R.drawable.ic_storage_box);
                    break;
                case ApiContract.Storage.DROPBOX:
                    mImage.setImageResource(R.drawable.ic_storage_dropbox);
                    break;
                case ApiContract.Storage.SHAREPOINT:
                    mImage.setImageResource(R.drawable.ic_storage_sharepoint);
                    break;
                case ApiContract.Storage.GOOGLEDRIVE:
                    mImage.setImageResource(R.drawable.ic_storage_google);
                    break;
                case ApiContract.Storage.ONEDRIVE:
                    mImage.setImageResource(R.drawable.ic_storage_onedrive);
                    break;
                case ApiContract.Storage.YANDEX:
                    mImage.setImageResource(R.drawable.ic_storage_yandex);
                    break;
                case ApiContract.Storage.OWNCLOUD:
                    mImage.setImageResource(R.drawable.ic_storage_owncloud);
                    break;
                case ApiContract.Storage.NEXTCLOUD:
                    mImage.setImageResource(R.drawable.ic_storage_nextcloud);
                    break;
                case ApiContract.Storage.WEBDAV:
                    mImage.setImageResource(R.drawable.ic_storage_webdav);
                    break;
            }
        }
    }
}
